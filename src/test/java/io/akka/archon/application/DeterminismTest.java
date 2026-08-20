package io.akka.archon.application;

import io.akka.archon.domain.Preset;
import io.akka.archon.domain.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-001 §3 rule 23 — the whole capability is a pure function of its inputs.
 *
 * <p>Repetition is the only way to show this from outside: a decision that consulted a
 * clock, a file or an environment variable would eventually answer differently, and one
 * that carried state between calls would answer differently the second time.
 */
class DeterminismTest {

  @Test
  void sameInputsSameAnswerOnEveryCall() {
    var profile = ModelProfile.of("claude").withAlias("@codexy", new Preset("codex", "gpt-5.5", "high"));
    var scope = RunPinning.workflowScope(new RunPinning.Workflow(null, "large", null), "claude", Map.of(), profile);
    var node = new WorkflowNode("n", "claude", "@codexy", null, List.of("Reed", "Task", "Bash(git:*)"), List.of("Nope"));
    var caps = Providers.require("claude");

    var firstPin = RunPinning.resolve(node, scope, Map.of(), profile);
    var firstFindings = ToolPolicy.check(node, caps);
    var firstTools = ToolPolicy.effective(node.allowedTools(), node.deniedTools(), Providers.require("pi"));

    for (int i = 0; i < 50; i++) {
      assertThat(RunPinning.resolve(node, scope, Map.of(), profile)).isEqualTo(firstPin);
      assertThat(ToolPolicy.check(node, caps)).isEqualTo(firstFindings);
      assertThat(ToolPolicy.effective(node.allowedTools(), node.deniedTools(), Providers.require("pi")))
          .isEqualTo(firstTools);
    }

    // A fresh profile built the same way answers the same way — nothing is memoised
    // between profiles either.
    var rebuilt = ModelProfile.of("claude").withAlias("@codexy", new Preset("codex", "gpt-5.5", "high"));
    var rebuiltScope =
        RunPinning.workflowScope(new RunPinning.Workflow(null, "large", null), "claude", Map.of(), rebuilt);
    assertThat(RunPinning.resolve(node, rebuiltScope, Map.of(), rebuilt)).isEqualTo(firstPin);
  }
}
