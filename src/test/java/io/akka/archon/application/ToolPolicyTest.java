package io.akka.archon.application;

import io.akka.archon.domain.Finding;
import io.akka.archon.domain.Level;
import io.akka.archon.domain.ProviderCapabilities;
import io.akka.archon.domain.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SPEC-001 §3 rules 1-9 — what an agent may do. */
class ToolPolicyTest {

  private static final ProviderCapabilities CLAUDE = Providers.require("claude");
  private static final ProviderCapabilities CODEX = Providers.require("codex");
  private static final ProviderCapabilities PI = Providers.require("pi");

  private static List<Finding> check(ProviderCapabilities caps, List<String> allowed, List<String> denied) {
    return ToolPolicy.check(new WorkflowNode("n", null, null, null, allowed, denied), caps);
  }

  /** Rule 1. */
  @Test
  void unknownToolNameWarnsAndDoesNotFail() {
    List<Finding> findings = check(CLAUDE, List.of("Reed"), null);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).level()).isEqualTo(Level.WARNING);
    assertThat(findings.get(0).field()).isEqualTo("allowed_tools");
    assertThat(findings.get(0).message()).contains("Reed").contains("silently ignored at runtime");
    assertThat(findings).noneMatch(f -> f.level() == Level.ERROR);
  }

  /** Rule 2. */
  @Test
  void renamedToolNamesItsReplacement() {
    List<Finding> findings = check(CLAUDE, null, List.of("Task"));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).level()).isEqualTo(Level.WARNING);
    assertThat(findings.get(0).field()).isEqualTo("denied_tools");
    assertThat(findings.get(0).message()).contains("'Task' was renamed to 'Agent'");
    assertThat(findings.get(0).suggestions()).containsExactly("Agent");
  }

  /** Rule 3. */
  @Test
  void nearMissesAreSuggestedNearestFirst() {
    assertThat(check(CLAUDE, List.of("Reed"), null).get(0).suggestions()).containsExactly("Read");

    // TaskList is one edit away, TaskGet two; both are inside the threshold for an
    // eight-character name, and the nearer one comes first.
    assertThat(check(CLAUDE, List.of("TaskLest"), null).get(0).suggestions())
        .containsExactly("TaskList", "TaskGet");

    assertThat(check(CLAUDE, List.of("CompletelyDifferent"), null).get(0).suggestions()).isEmpty();

    // The cap belongs to the matcher, not to Claude's vocabulary: no real tool name has
    // three neighbours inside the threshold.
    assertThat(FuzzyMatch.findSimilar("Reax", List.of("Read", "Real", "Ream", "Reap", "Rear")))
        .containsExactly("Read", "Real", "Ream");
  }

  /** Rule 4. */
  @Test
  void permissionSpecifierIsCheckedOnItsBaseName() {
    assertThat(check(CLAUDE, List.of("Bash(git:*)"), null)).isEmpty();
    assertThat(check(CLAUDE, List.of("Bosh(git:*)"), null).get(0).message()).contains("'Bosh'");
  }

  /** Rule 5. */
  @Test
  void mcpNamesAreNeverChecked() {
    assertThat(check(CLAUDE, List.of("mcp__srv", "mcp__srv__tool", "mcp__srv__*"), null)).isEmpty();
    assertThat(check(CLAUDE, List.of("", "  ", "(x)"), null)).isEmpty();
  }

  /** Rule 6. */
  @Test
  void providerWithoutRestrictionsWarnsOnceForBothFields() {
    List<Finding> findings = check(CODEX, List.of("Read"), List.of("Bash"));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).field()).isEqualTo("allowed_tools/denied_tools");
    assertThat(findings.get(0).level()).isEqualTo(Level.WARNING);
    assertThat(findings.get(0).message()).contains("not supported by provider 'codex'");
  }

  /** Rule 7 — the set arithmetic. */
  @Test
  void effectiveSetArithmetic() {
    // Allow-list wins, in the order written, with duplicates removed.
    assertThat(ToolPolicy.effective(List.of("read", "edit", "read"), null, PI).tools())
        .containsExactly("read", "edit");

    // Denial without an allow-list subtracts from the provider's full built-in set.
    assertThat(ToolPolicy.effective(null, List.of("bash"), PI).tools())
        .containsExactly("read", "edit", "write", "grep", "find", "ls");

    // Denial also subtracts from an allow-list.
    assertThat(ToolPolicy.effective(List.of("read", "bash"), List.of("bash"), PI).tools())
        .containsExactly("read");
  }

  /** Rule 7 — the two cases that look alike and are not. */
  @Test
  void emptyAllowListIsNotAbsent() {
    assertThat(ToolPolicy.effective(List.of(), null, PI).tools()).isEmpty();
    assertThat(ToolPolicy.effective(null, null, PI).tools()).isNull();
    assertThat(ToolPolicy.effective(null, null, PI).providerDefault()).isTrue();
    assertThat(ToolPolicy.effective(List.of(), null, PI).providerDefault()).isFalse();
  }

  /** Rule 8. */
  @Test
  void unknownNamesAreReportedNotSwallowed() {
    ToolPolicy.EffectiveTools result = ToolPolicy.effective(null, List.of("bash", "Nope"), PI);

    assertThat(result.unknown()).containsExactly("Nope");
    assertThat(result.tools()).doesNotContain("Nope", "bash");
  }

  /** Rule 9. */
  @Test
  void providerWithoutVocabularySkipsTheCheck() {
    assertThat(PI.toolRestrictions()).isTrue();
    assertThat(PI.knownToolNames()).isNull();
    assertThat(check(PI, List.of("NotATool"), null)).isEmpty();
  }
}
