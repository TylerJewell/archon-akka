package io.akka.archon.application;

import io.akka.archon.domain.Preset;
import io.akka.archon.domain.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Times the two decisions in-process, so {@code bench/REPORT.md} can separate what the
 * computation costs from what one HTTP round trip costs.
 *
 * <p>Not named {@code *Test}, so it does not run with the suite — it measures rather than
 * asserts, and a timing that fails a build for being slow on a busy machine is noise.
 * Run it deliberately:
 *
 * <pre>python toolkit/akka_test.py archon-akka test -Dtest=TimingHarness</pre>
 *
 * <p>The two groups and the operation counts match {@code bench/source_answers.ts} and
 * {@code bench/port_answers.py}: 20 pinning operations and 11 tool operations per pass,
 * 200 passes, after three warm-up passes.
 */
class TimingHarness {

  private static final int WARMUP = 3;
  private static final int PASSES = 200;
  private static final int PINNING_PER_PASS = 20;
  private static final int TOOLS_PER_PASS = 11;

  @Test
  void timeTheTwoDecisions() throws Exception {
    StringBuilder json = new StringBuilder("{\n");
    json.append(measure("pinning", PINNING_PER_PASS, TimingHarness::onePinning)).append(",\n");
    json.append(measure("tools", TOOLS_PER_PASS, TimingHarness::oneToolCheck)).append("\n}\n");

    Files.writeString(Path.of("..", "archon-port", "bench", "port-inprocess-timing.json"), json);
  }

  private static String measure(String label, int perPass, Runnable operation) {
    for (int i = 0; i < WARMUP * perPass; i++) {
      operation.run();
    }
    long started = System.nanoTime();
    for (int i = 0; i < PASSES * perPass; i++) {
      operation.run();
    }
    double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
    int operations = PASSES * perPass;

    System.out.printf(
        Locale.ROOT, "%s: %d operations in %.1f ms - %.4f ms/op%n",
        label, operations, elapsedMs, elapsedMs / operations);
    return String.format(
        Locale.ROOT,
        "  \"%s\": { \"operations\": %d, \"totalMs\": %.3f, \"msPerOperation\": %.6f }",
        label, operations, elapsedMs, elapsedMs / operations);
  }

  private static void onePinning() {
    var profile = ModelProfile.of("codex").withAlias("@codexy", new Preset("codex", "gpt-5.5", "high"));
    var scope = RunPinning.workflowScope(
        new RunPinning.Workflow(null, "large", null), "codex", Map.of(), profile);
    var node = new WorkflowNode("n", "claude", "@codexy", null, null, null);

    RunPinning.resolve(node, scope, Map.of(), profile);
    EffortLadder.forPreset("codex", "max");
    EffortLadder.clamp("max", Providers.require("codex").supportedEfforts());
  }

  private static void oneToolCheck() {
    var node = new WorkflowNode(
        "n", "claude", null, null, List.of("Reed", "Task", "Bash(git:*)"), List.of("Nope"));

    ToolPolicy.check(node, Providers.require("claude"));
    ToolPolicy.effective(node.allowedTools(), node.deniedTools(), Providers.require("pi"));
  }
}
