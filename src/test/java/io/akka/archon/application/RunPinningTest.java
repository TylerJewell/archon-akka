package io.akka.archon.application;

import io.akka.archon.domain.Origin;
import io.akka.archon.domain.Preset;
import io.akka.archon.domain.Tier;
import io.akka.archon.domain.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** SPEC-001 §3 rules 15-19 — which provider, model and reasoning depth a node runs at. */
class RunPinningTest {

  private static WorkflowNode node(String id, String provider, String model, String effort) {
    return new WorkflowNode(id, provider, model, effort, null, null);
  }

  /** Rule 15. */
  @Test
  void presetProviderWinsAndConflictIsReported() {
    var profile = ModelProfile.of("claude")
        .withAlias("@codexy", new Preset("codex", "gpt-5.5", "high"));
    var scope = RunPinning.workflowScope(new RunPinning.Workflow(null, null, null), "claude", Map.of(), profile);

    var resolution = RunPinning.resolve(node("n", "claude", "@codexy", null), scope, Map.of(), profile);

    assertThat(resolution.provider()).isEqualTo("codex");
    assertThat(resolution.model()).isEqualTo("gpt-5.5");
    assertThat(resolution.providerOrigin()).isEqualTo(Origin.MODEL_REF);
    assertThat(resolution.modelOrigin()).isEqualTo(Origin.MODEL_REF);
    assertThat(resolution.providerConflict())
        .isEqualTo(new RunPinning.ProviderConflict("claude", "codex", "@codexy"));
  }

  /** Rule 16. */
  @Test
  void workflowModelIsInheritedOnlyWithinTheSameProvider() {
    var scope = RunPinning.workflowScope(
        new RunPinning.Workflow("claude", "wf-model", null), "claude", Map.of(), null);

    var same = RunPinning.resolve(node("a", null, null, null), scope, Map.of(), null);
    assertThat(same.model()).isEqualTo("wf-model");
    assertThat(same.modelOrigin()).isEqualTo(Origin.WORKFLOW);

    var other = RunPinning.resolve(
        node("b", "codex", null, null), scope, Map.of("codex", "codex-default"), null);
    assertThat(other.model()).isEqualTo("codex-default");
    assertThat(other.modelOrigin()).isEqualTo(Origin.ASSISTANT_CONFIG);

    var nothing = RunPinning.resolve(node("c", "opencode", null, null), scope, Map.of(), null);
    assertThat(nothing.model()).isNull();
    assertThat(nothing.modelOrigin()).isEqualTo(Origin.UNSET);
  }

  /** Rule 17. */
  @Test
  void workflowPresetReachesABareNodeOnly() {
    var profile = ModelProfile.of("claude");
    var scope = RunPinning.workflowScope(new RunPinning.Workflow(null, "large", null), "claude", Map.of(), profile);

    var bare = RunPinning.resolve(node("a", null, null, null), scope, Map.of(), profile);
    assertThat(bare.preset()).isEqualTo(new Preset("claude", "opus", null));
    assertThat(bare.tier()).isEqualTo(Tier.LARGE);

    var own = RunPinning.resolve(node("b", null, "lit-model", null), scope, Map.of(), profile);
    assertThat(own.preset()).isNull();
    assertThat(own.tier()).isNull();
    assertThat(own.model()).isEqualTo("lit-model");
    assertThat(own.modelOrigin()).isEqualTo(Origin.NODE);
  }

  /** Rule 18. */
  @Test
  void effortResolvesNodeThenWorkflowThenPreset() {
    var profile = ModelProfile.of("codex"); // codex's built-in tiers carry an effort
    var presetOnly = RunPinning.workflowScope(new RunPinning.Workflow(null, "large", null), "codex", Map.of(), profile);
    var withWorkflowEffort =
        RunPinning.workflowScope(new RunPinning.Workflow(null, "large", "low"), "codex", Map.of(), profile);

    var fromPreset = RunPinning.resolve(node("a", null, null, null), presetOnly, Map.of(), profile);
    assertThat(fromPreset.effort()).isEqualTo("high");
    assertThat(fromPreset.effortOrigin()).isEqualTo(Origin.MODEL_REF);

    var fromWorkflow = RunPinning.resolve(node("b", null, null, null), withWorkflowEffort, Map.of(), profile);
    assertThat(fromWorkflow.effort()).isEqualTo("low");
    assertThat(fromWorkflow.effortOrigin()).isEqualTo(Origin.WORKFLOW);

    var fromNode = RunPinning.resolve(node("c", null, null, "minimal"), withWorkflowEffort, Map.of(), profile);
    assertThat(fromNode.effort()).isEqualTo("minimal");
    assertThat(fromNode.effortOrigin()).isEqualTo(Origin.NODE);
  }

  /** Rule 19. */
  @Test
  void declaredEffortIsKeptApartFromThePresetEffort() {
    var profile = ModelProfile.of("codex");
    var scope = RunPinning.workflowScope(new RunPinning.Workflow(null, "large", null), "codex", Map.of(), profile);

    var fromPreset = RunPinning.resolve(node("a", null, null, null), scope, Map.of(), profile);
    assertThat(fromPreset.declaredEffort()).isNull();
    assertThat(fromPreset.effort()).isEqualTo("high");

    var declared = RunPinning.resolve(node("b", null, null, "low"), scope, Map.of(), profile);
    assertThat(declared.declaredEffort()).isEqualTo("low");
    assertThat(declared.effort()).isEqualTo("low");

    // The other route to a preset effort: the node names the tier itself, so the preset
    // arrives through the node rather than through the workflow.
    var bare = RunPinning.workflowScope(new RunPinning.Workflow(null, null, null), "codex", Map.of(), profile);
    var nodeTier = RunPinning.resolve(node("c", null, "large", null), bare, Map.of(), profile);
    assertThat(nodeTier.declaredEffort()).isNull();
    assertThat(nodeTier.effort()).isEqualTo("high");
    assertThat(nodeTier.effortOrigin()).isEqualTo(Origin.MODEL_REF);
  }
}
