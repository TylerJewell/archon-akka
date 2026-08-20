package io.akka.archon.application;

import io.akka.archon.domain.ModelSpec;
import io.akka.archon.domain.Origin;
import io.akka.archon.domain.Preset;
import io.akka.archon.domain.Tier;
import io.akka.archon.domain.WorkflowNode;

import java.util.Map;

/**
 * How a run is pinned: which provider, model and reasoning depth one node runs at, and
 * where each of the three came from.
 *
 * <p>Two conditions are easy to get backwards and are the reason this is one function
 * rather than three lookups. A workflow-level model applies only when the node resolves to
 * the workflow's own provider, and a workflow-level preset applies only when the node
 * declares no model reference of its own. Both exist so a node that switches provider
 * never inherits the other provider's model string.
 */
public final class RunPinning {

  /** The workflow-level fields a node may inherit. Every one may be absent. */
  public record Workflow(String provider, String model, String effort) {}

  /** What the workflow level settled on, before any node is considered. */
  public record Scope(
      String provider,
      String model,
      Preset preset,
      Tier tier,
      Tier matchedTier,
      String effort,
      Origin providerOrigin) {}

  /** A node named one provider and its model reference resolved to another. */
  public record ProviderConflict(String declared, String resolved, String modelRef) {}

  /**
   * @param declaredEffort the depth the author wrote, before a preset fills one in — kept
   *     apart from {@code effort} because the provider capability gate is applied to this
   *     one, and reporting a depth that was never applied would misreport the run
   * @param matchedTier which tier the fallback chain actually landed on, where a tier was
   *     used at all
   */
  public record Resolution(
      String provider,
      String model,
      String effort,
      String declaredEffort,
      Tier tier,
      Tier matchedTier,
      Preset preset,
      Origin providerOrigin,
      Origin modelOrigin,
      Origin effortOrigin,
      ProviderConflict providerConflict) {}

  private RunPinning() {}

  /** Derive the workflow-level fallbacks a node will inherit from. */
  public static Scope workflowScope(
      Workflow workflow,
      String defaultAssistant,
      Map<String, String> assistantModels,
      ModelProfile profile) {
    String provider = workflow.provider() != null ? workflow.provider() : defaultAssistant;
    String model = null;
    Preset preset = null;
    Tier tier = null;
    Tier matchedTier = null;

    if (workflow.model() != null && profile != null) {
      ModelSpec spec = profile.resolve(workflow.model());
      if (spec instanceof ModelSpec.FromPreset fromPreset) {
        preset = fromPreset.preset();
        provider = preset.provider();
        model = preset.model();
        Tier asked = Tier.of(workflow.model());
        if (asked != null) {
          tier = asked;
          matchedTier = profile.resolveTier(asked).matched();
        }
      } else {
        model = ((ModelSpec.Literal) spec).value();
      }
    } else if (workflow.model() != null) {
      model = workflow.model();
    }

    if (model == null) {
      model = assistantModels.get(provider);
    }

    // The preset is checked first because when one resolves, its provider is what won —
    // reporting the overridden declaration as the origin would name the loser.
    Origin providerOrigin = preset != null
        ? Origin.MODEL_REF
        : workflow.provider() != null ? Origin.WORKFLOW : Origin.DEFAULT_ASSISTANT;

    return new Scope(provider, model, preset, tier, matchedTier, workflow.effort(), providerOrigin);
  }

  /** Resolve one node against a workflow scope. */
  public static Resolution resolve(
      WorkflowNode node, Scope scope, Map<String, String> assistantModels, ModelProfile profile) {
    String provider = node.provider() != null ? node.provider() : scope.provider();
    Origin providerOrigin = node.provider() != null ? Origin.NODE : scope.providerOrigin();
    Preset preset = null;
    String model = null;
    Origin modelOrigin = Origin.UNSET;
    ProviderConflict conflict = null;
    Tier nodeTier = null;
    Tier nodeMatchedTier = null;

    if (node.model() != null) {
      modelOrigin = Origin.NODE;
      if (profile != null) {
        ModelSpec spec = profile.resolve(node.model());
        if (spec instanceof ModelSpec.FromPreset fromPreset) {
          preset = fromPreset.preset();
          provider = preset.provider();
          model = preset.model();
          modelOrigin = Origin.MODEL_REF;
          providerOrigin = Origin.MODEL_REF;
          nodeTier = Tier.of(node.model());
          if (nodeTier != null) {
            nodeMatchedTier = profile.resolveTier(nodeTier).matched();
          }
          if (node.provider() != null && !node.provider().equals(provider)) {
            conflict = new ProviderConflict(node.provider(), provider, node.model());
          }
        } else {
          model = ((ModelSpec.Literal) spec).value();
        }
      } else {
        model = node.model();
      }
    }

    if (model == null) {
      // No further fallback within the workflow's own provider: the caller has already
      // folded the assistant default into the scope's model.
      if (provider.equals(scope.provider())) {
        model = scope.model();
        modelOrigin = model != null ? Origin.WORKFLOW : Origin.UNSET;
      } else {
        model = assistantModels.get(provider);
        modelOrigin = model != null ? Origin.ASSISTANT_CONFIG : Origin.UNSET;
      }
    }

    boolean inheritsFromWorkflow = node.model() == null && provider.equals(scope.provider());
    Preset effectivePreset = preset != null ? preset : inheritsFromWorkflow ? scope.preset() : null;

    String declaredEffort = node.effort() != null ? node.effort() : scope.effort();
    String effort = declaredEffort != null
        ? declaredEffort
        : effectivePreset != null ? effectivePreset.effort() : null;
    Origin effortOrigin = node.effort() != null
        ? Origin.NODE
        : scope.effort() != null
            ? Origin.WORKFLOW
            : effectivePreset != null && effectivePreset.effort() != null
                ? Origin.MODEL_REF
                : Origin.UNSET;

    Tier tier = nodeTier != null ? nodeTier : inheritsFromWorkflow ? scope.tier() : null;
    Tier matchedTier =
        nodeTier != null ? nodeMatchedTier : inheritsFromWorkflow ? scope.matchedTier() : null;

    return new Resolution(
        provider,
        model,
        effort,
        declaredEffort,
        tier,
        matchedTier,
        effectivePreset,
        providerOrigin,
        modelOrigin,
        effortOrigin,
        conflict);
  }
}
