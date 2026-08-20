package io.akka.archon.application;

import io.akka.archon.domain.ProviderCapabilities;

import java.util.List;
import java.util.Map;

/**
 * The five providers Archon ships with, and what each of them can honour.
 *
 * <p>Registration in the original is dynamic — a provider registers itself at process
 * start and an unregistered name is answered as "no reasoning control". That answer is
 * reproduced here (see {@link EffortLadder#validFor}); what is not reproduced is the
 * ability to add a provider at runtime, because nothing in the ported slice decides
 * anything about a provider it has never heard of.
 *
 * <p>The vocabularies are hand-audited lists in the original, against one SDK version.
 * That is the whole reason a tool-name finding can only ever be a warning.
 */
public final class Providers {

  private static final List<String> LADDER =
      List.of("minimal", "low", "medium", "high", "xhigh", "max");

  private static final List<String> CLAUDE_TOOLS = List.of(
      "Agent", "AskUserQuestion", "Bash", "Edit", "ExitPlanMode", "Glob", "Grep",
      "ListMcpResourcesTool", "NotebookEdit", "PowerShell", "Read", "ReadMcpResourceTool",
      "Skill", "SlashCommand", "TaskCreate", "TaskGet", "TaskList", "TaskOutput", "TaskStop",
      "TaskUpdate", "TodoWrite", "WebFetch", "WebSearch", "Write");

  private static final Map<String, String> CLAUDE_RENAMED = Map.of(
      "Task", "Agent",
      "BashOutput", "TaskOutput",
      "KillShell", "TaskStop",
      "MultiEdit", "Edit");

  private static final List<String> PI_TOOLS =
      List.of("read", "bash", "edit", "write", "grep", "find", "ls");

  private static final Map<String, ProviderCapabilities> BY_ID = Map.of(
      "claude", new ProviderCapabilities(
          "claude", true, true, CLAUDE_TOOLS, CLAUDE_RENAMED, CLAUDE_TOOLS, CLAUDE_TOOLS,
          List.of("low", "medium", "high", "xhigh", "max")),
      "codex", new ProviderCapabilities(
          "codex", false, true, null, Map.of(), List.of(), List.of(),
          List.of("minimal", "low", "medium", "high", "xhigh")),
      "pi", new ProviderCapabilities(
          "pi", true, true, null, Map.of(), PI_TOOLS, List.of("read", "bash", "edit", "write"),
          LADDER),
      "copilot", new ProviderCapabilities(
          "copilot", true, true, null, Map.of(), List.of(), List.of(),
          List.of("low", "medium", "high", "xhigh")),
      "opencode", new ProviderCapabilities(
          "opencode", true, false, null, Map.of(), List.of(), List.of(), List.of()));

  private Providers() {}

  /** What this provider can honour, or null where the name is not one of the five. */
  public static ProviderCapabilities find(String id) {
    return id == null ? null : BY_ID.get(id);
  }

  public static ProviderCapabilities require(String id) {
    ProviderCapabilities caps = find(id);
    if (caps == null) {
      throw new IllegalArgumentException(
          "Unknown provider '" + id + "'. Known: " + String.join(", ", BY_ID.keySet().stream().sorted().toList()));
    }
    return caps;
  }
}
