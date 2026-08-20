package io.akka.archon.domain;

import java.util.List;

/**
 * One step of a workflow, carrying only the fields the two determinism decisions read.
 * Every field but the id may be absent, and absent means "inherit", not "none".
 */
public record WorkflowNode(
    String id,
    String provider,
    String model,
    String effort,
    List<String> allowedTools,
    List<String> deniedTools) {

  public WorkflowNode {
    allowedTools = allowedTools == null ? null : List.copyOf(allowedTools);
    deniedTools = deniedTools == null ? null : List.copyOf(deniedTools);
  }
}
