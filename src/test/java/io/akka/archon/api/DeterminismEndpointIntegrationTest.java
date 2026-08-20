package io.akka.archon.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-001 §5 — every rule reachable over HTTP, so the wire shapes are exercised and not
 * only the application classes behind them.
 *
 * <p>The wire carries the spellings coleam00/Archon uses — {@code warning},
 * {@code model ref}, {@code large} — rather than the port's own enumeration names, so a
 * caller comparing the two systems compares answers rather than vocabularies.
 */
public class DeterminismEndpointIntegrationTest extends TestKitSupport {

  private DeterminismEndpoint.ToolsResponse tools(
      String provider, List<String> allowed, List<String> denied) {
    return httpClient
        .POST("/determinism/tools")
        .withRequestBody(new DeterminismEndpoint.ToolsRequest(provider, allowed, denied))
        .responseBodyAs(DeterminismEndpoint.ToolsResponse.class)
        .invoke()
        .body();
  }

  @Test
  void toolFindingsAndTheEffectiveSetComeBackTogether() {
    var claude = tools("claude", List.of("Reed", "Task", "Bash(git:*)", "mcp__srv__x"), null);

    assertThat(claude.findings()).hasSize(2);
    assertThat(claude.findings()).allMatch(f -> f.level().equals("warning"));
    assertThat(claude.findings().get(0).message()).contains("Reed");
    assertThat(claude.findings().get(1).message()).contains("renamed to 'Agent'");

    var codex = tools("codex", List.of("Read"), List.of("Bash"));
    assertThat(codex.findings()).hasSize(1);
    assertThat(codex.findings().get(0).field()).isEqualTo("allowed_tools/denied_tools");

    var pi = tools("pi", null, List.of("bash", "Nope"));
    assertThat(pi.effectiveTools()).containsExactly("read", "edit", "write", "grep", "find", "ls");
    assertThat(pi.unknownTools()).containsExactly("Nope");
    assertThat(pi.providerDefault()).isFalse();

    var untouched = tools("pi", null, null);
    assertThat(untouched.providerDefault()).isTrue();
    assertThat(untouched.effectiveTools()).isNull();
  }

  @Test
  void anUnknownProviderIsRefusedRatherThanGuessed() {
    var status = httpClient
        .POST("/determinism/tools")
        .withRequestBody(new DeterminismEndpoint.ToolsRequest("nosuchprovider", List.of("Read"), null))
        .invoke()
        .httpResponse()
        .status();

    assertThat(status.intValue()).isEqualTo(400);
  }

  @Test
  void pinningReportsEveryResolvedValueAndWhereItCameFrom() {
    var request = new DeterminismEndpoint.PinRequest(
        "claude",
        Map.of(),
        Map.of("@codexy", new DeterminismEndpoint.PresetBody("codex", "gpt-5.5", "high")),
        Map.of(),
        new DeterminismEndpoint.NodeBody("n", "claude", "@codexy", null),
        new DeterminismEndpoint.WorkflowBody(null, null, null));

    var response = httpClient
        .POST("/determinism/pin")
        .withRequestBody(request)
        .responseBodyAs(DeterminismEndpoint.PinResponse.class)
        .invoke()
        .body();

    assertThat(response.provider()).isEqualTo("codex");
    assertThat(response.model()).isEqualTo("gpt-5.5");
    assertThat(response.effort()).isEqualTo("high");
    assertThat(response.providerOrigin()).isEqualTo("model ref");
    assertThat(response.declaredEffort()).isNull();
    assertThat(response.tier()).isNull();
    assertThat(response.providerConflict().declared()).isEqualTo("claude");
    assertThat(response.providerConflict().resolved()).isEqualTo("codex");
  }

  @Test
  void anUnresolvableModelReferenceIsRefused() {
    var request = new DeterminismEndpoint.PinRequest(
        "claude",
        Map.of(),
        Map.of(),
        Map.of(),
        new DeterminismEndpoint.NodeBody("n", null, "@nope", null),
        new DeterminismEndpoint.WorkflowBody(null, null, null));

    var response = httpClient.POST("/determinism/pin").withRequestBody(request).invoke().httpResponse();

    assertThat(response.status().intValue()).isEqualTo(400);
  }

  @Test
  void theEffortGateAndTheClampAreBothReachable() {
    var offLadder = httpClient
        .POST("/determinism/effort")
        .withRequestBody(new DeterminismEndpoint.EffortRequest("claude", "ultra"))
        .responseBodyAs(DeterminismEndpoint.EffortResponse.class)
        .invoke()
        .body();
    assertThat(offLadder.ok()).isFalse();
    assertThat(offLadder.reason()).isEqualTo("unknown");
    assertThat(offLadder.clamped()).isNull();

    var clamped = httpClient
        .POST("/determinism/effort")
        .withRequestBody(new DeterminismEndpoint.EffortRequest("codex", "max"))
        .responseBodyAs(DeterminismEndpoint.EffortResponse.class)
        .invoke()
        .body();
    assertThat(clamped.ok()).isTrue();
    assertThat(clamped.clamped()).isEqualTo("xhigh");

    var noControl = httpClient
        .POST("/determinism/effort")
        .withRequestBody(new DeterminismEndpoint.EffortRequest("opencode", "high"))
        .responseBodyAs(DeterminismEndpoint.EffortResponse.class)
        .invoke()
        .body();
    assertThat(noControl.ok()).isFalse();
    assertThat(noControl.reason()).isEqualTo("unsupported");
    assertThat(noControl.valid()).isNull();
  }

  @Test
  void tierFallbackReportsWhichTierActuallyMatched() {
    var request = new DeterminismEndpoint.PinRequest(
        "nosuchprovider",
        Map.of("small", new DeterminismEndpoint.PresetBody("claude", "s", null)),
        Map.of(),
        Map.of(),
        new DeterminismEndpoint.NodeBody("n", null, "large", null),
        new DeterminismEndpoint.WorkflowBody(null, null, null));

    var response = httpClient
        .POST("/determinism/pin")
        .withRequestBody(request)
        .responseBodyAs(DeterminismEndpoint.PinResponse.class)
        .invoke()
        .body();

    assertThat(response.model()).isEqualTo("s");
    assertThat(response.tier()).isEqualTo("large");
    assertThat(response.matchedTier()).isEqualTo("small");
  }
}
