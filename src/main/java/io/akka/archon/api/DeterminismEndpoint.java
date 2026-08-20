package io.akka.archon.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import io.akka.archon.application.EffortLadder;
import io.akka.archon.application.ModelProfile;
import io.akka.archon.application.Providers;
import io.akka.archon.application.RunPinning;
import io.akka.archon.application.ToolPolicy;
import io.akka.archon.domain.Finding;
import io.akka.archon.domain.Preset;
import io.akka.archon.domain.ProviderCapabilities;
import io.akka.archon.domain.Tier;
import io.akka.archon.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

/**
 * The two determinism decisions, over HTTP — see {@code specs/SPEC-001-archon.md}.
 *
 * <p>Every configuration layer arrives in the request. Nothing here reads a file, a clock
 * or an environment variable, so the same request always produces the same answer, and a
 * caller wanting a run pinned reproducibly holds on to the request rather than trusting
 * this service to remember anything.
 *
 * <p>Opened up for access from the public internet to make this port easy to try out; a
 * production service would scope this more tightly (see {@code akka-sdk} access-control
 * docs).
 */
@HttpEndpoint("/determinism")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class DeterminismEndpoint extends AbstractHttpEndpoint {

  // --- what an agent may do -------------------------------------------------

  public record ToolsRequest(String provider, List<String> allowedTools, List<String> deniedTools) {}

  /** A finding on the wire. Carries the level as the original spells it. */
  public record FindingView(
      String level, String field, String message, String hint, List<String> suggestions) {
    static FindingView of(Finding finding) {
      return new FindingView(
          finding.level().wire(),
          finding.field(),
          finding.message(),
          finding.hint(),
          finding.suggestions());
    }
  }

  /**
   * @param effectiveTools null when neither field was set, meaning the provider keeps its
   *     own default set — a different answer from an empty list
   */
  public record ToolsResponse(
      List<FindingView> findings,
      List<String> effectiveTools,
      List<String> unknownTools,
      boolean providerDefault) {}

  @Post("/tools")
  public ToolsResponse tools(ToolsRequest request) {
    ProviderCapabilities caps = known(request.provider());
    var node = new WorkflowNode("request", request.provider(), null, null,
        request.allowedTools(), request.deniedTools());

    List<FindingView> findings = ToolPolicy.check(node, caps).stream().map(FindingView::of).toList();
    ToolPolicy.EffectiveTools effective =
        ToolPolicy.effective(request.allowedTools(), request.deniedTools(), caps);

    return new ToolsResponse(
        findings, effective.tools(), effective.unknown(), effective.providerDefault());
  }

  // --- how a run is pinned --------------------------------------------------

  public record PresetBody(String provider, String model, String effort) {
    Preset toDomain(String name) {
      try {
        return new Preset(provider, model, effort);
      } catch (IllegalArgumentException e) {
        throw HttpException.badRequest("Preset '" + name + "': " + e.getMessage());
      }
    }
  }

  public record NodeBody(String id, String provider, String model, String effort) {}

  public record WorkflowBody(String provider, String model, String effort) {}

  /**
   * @param tiers overrides keyed by tier keyword, applied over the provider defaults
   * @param aliases custom presets, keyed by a name beginning {@code @}
   * @param assistantModels each provider's configured default model
   */
  public record PinRequest(
      String defaultProvider,
      Map<String, PresetBody> tiers,
      Map<String, PresetBody> aliases,
      Map<String, String> assistantModels,
      NodeBody node,
      WorkflowBody workflow) {}

  public record ConflictView(String declared, String resolved, String modelRef) {}

  public record PinResponse(
      String provider,
      String model,
      String effort,
      String declaredEffort,
      String tier,
      String matchedTier,
      PresetBody preset,
      String providerOrigin,
      String modelOrigin,
      String effortOrigin,
      ConflictView providerConflict) {}

  @Post("/pin")
  public PinResponse pin(PinRequest request) {
    ModelProfile profile = ModelProfile.of(request.defaultProvider());
    try {
      for (var entry : orEmpty(request.tiers()).entrySet()) {
        profile = profile.withTier(entry.getKey(), entry.getValue().toDomain(entry.getKey()));
      }
      for (var entry : orEmpty(request.aliases()).entrySet()) {
        profile = profile.withAlias(entry.getKey(), entry.getValue().toDomain(entry.getKey()));
      }

      Map<String, String> assistantModels =
          request.assistantModels() == null ? Map.of() : request.assistantModels();
      var workflow = request.workflow() == null
          ? new RunPinning.Workflow(null, null, null)
          : new RunPinning.Workflow(
              request.workflow().provider(), request.workflow().model(), request.workflow().effort());

      var scope = RunPinning.workflowScope(
          workflow, request.defaultProvider(), assistantModels, profile);
      var node = new WorkflowNode(
          request.node().id(), request.node().provider(), request.node().model(),
          request.node().effort(), null, null);

      var resolution = RunPinning.resolve(node, scope, assistantModels, profile);
      return new PinResponse(
          resolution.provider(),
          resolution.model(),
          resolution.effort(),
          resolution.declaredEffort(),
          wire(resolution.tier()),
          wire(resolution.matchedTier()),
          resolution.preset() == null
              ? null
              : new PresetBody(
                  resolution.preset().provider(),
                  resolution.preset().model(),
                  resolution.preset().effort()),
          resolution.providerOrigin().wire(),
          resolution.modelOrigin().wire(),
          resolution.effortOrigin().wire(),
          resolution.providerConflict() == null
              ? null
              : new ConflictView(
                  resolution.providerConflict().declared(),
                  resolution.providerConflict().resolved(),
                  resolution.providerConflict().modelRef()));
    } catch (IllegalArgumentException e) {
      // A reference that resolves to nothing is refused rather than defaulted: a run
      // pinned to something nobody configured is not pinned.
      throw HttpException.badRequest(e.getMessage());
    }
  }

  // --- reasoning depth ------------------------------------------------------

  public record EffortRequest(String provider, String effort) {}

  /**
   * @param reason {@code unsupported} where the provider has no reasoning control at all,
   *     {@code unknown} where the rung is off the ladder, null where the rung was accepted
   * @param valid the rungs that would have been accepted — carried only on the
   *     {@code unknown} refusal, which is the one a caller can act on
   * @param clamped where the rung lands inside this provider's own vocabulary, null when
   *     it was not accepted in the first place
   */
  public record EffortResponse(boolean ok, String reason, List<String> valid, String clamped) {}

  @Post("/effort")
  public EffortResponse effort(EffortRequest request) {
    ProviderCapabilities caps = known(request.provider());
    EffortLadder.Verdict verdict = EffortLadder.forPreset(request.provider(), request.effort());

    return new EffortResponse(
        verdict.ok(),
        verdict.reason() == null ? null : verdict.reason().wire(),
        verdict.ok() ? null : verdict.valid(),
        verdict.ok() ? EffortLadder.clamp(request.effort(), caps.supportedEfforts()) : null);
  }

  private static String wire(Tier tier) {
    return tier == null ? null : tier.wire();
  }

  private static ProviderCapabilities known(String provider) {
    ProviderCapabilities caps = Providers.find(provider);
    if (caps == null) {
      throw HttpException.badRequest("Unknown provider '" + provider + "'");
    }
    return caps;
  }

  private static Map<String, PresetBody> orEmpty(Map<String, PresetBody> map) {
    return map == null ? Map.of() : map;
  }
}
