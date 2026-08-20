package io.akka.archon.application;

import io.akka.archon.domain.Finding;
import io.akka.archon.domain.ProviderCapabilities;
import io.akka.archon.domain.WorkflowNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What an agent may do: which of an author's tool names will have an effect, and what the
 * effective tool set works out to.
 *
 * <p>Every finding this produces is advisory. The layer underneath matches tool names as
 * opaque strings, so a name it does not recognise is ignored in silence — which is the
 * only reason this check exists, and the reason it may never refuse a workflow. The
 * vocabulary it checks against is maintained by hand against one SDK version, so a tool
 * a newer SDK adds would be reported as unknown; failing on that would break workflows
 * that are correct.
 */
public final class ToolPolicy {

  private static final String BOTH_FIELDS = "allowed_tools/denied_tools";
  private static final String MCP_PREFIX = "mcp__";

  private ToolPolicy() {}

  /**
   * The effective tool set for a node.
   *
   * @param tools null when neither field was set, meaning the provider keeps its own
   *     default set — distinct from an empty list, which means no tools at all
   * @param unknown names in either field the provider does not know; dropped from the set
   *     but reported, because a restriction that silently did nothing is worse than one
   *     that refused
   */
  public record EffectiveTools(List<String> tools, List<String> unknown, boolean providerDefault) {}

  /** What the author should know about the tool names they wrote. */
  public static List<Finding> check(WorkflowNode node, ProviderCapabilities caps) {
    List<Finding> findings = new ArrayList<>();
    boolean declaresEither = node.allowedTools() != null || node.deniedTools() != null;

    if (!caps.toolRestrictions()) {
      if (declaresEither) {
        findings.add(Finding.warning(
            BOTH_FIELDS,
            "Tool restrictions are not supported by provider '" + caps.id() + "' — this will be ignored",
            "Remove tool restriction fields or switch to a provider that supports them",
            List.of()));
      }
      return List.copyOf(findings);
    }

    // A provider that declares no vocabulary has nothing to check names against, and
    // flagging every name would be worse than flagging none.
    if (caps.knownToolNames() == null || caps.knownToolNames().isEmpty()) {
      return List.of();
    }

    checkNames("allowed_tools", node.allowedTools(), caps, findings);
    checkNames("denied_tools", node.deniedTools(), caps, findings);
    return List.copyOf(findings);
  }

  private static void checkNames(
      String field, List<String> entries, ProviderCapabilities caps, List<Finding> findings) {
    if (entries == null) {
      return;
    }
    for (String entry : entries) {
      // A permission rule wraps a base name: `Bash(git:*)` is a rule about Bash.
      int paren = entry.indexOf('(');
      String base = (paren >= 0 ? entry.substring(0, paren) : entry).trim();

      // MCP names are per-install and cannot be proven wrong from here.
      if (base.isEmpty() || base.startsWith(MCP_PREFIX) || caps.knownToolNames().contains(base)) {
        continue;
      }

      String renamed = caps.renamedTools().get(base);
      if (renamed != null) {
        findings.add(Finding.warning(
            field,
            "Tool '" + base + "' was renamed to '" + renamed + "' in the " + caps.id()
                + " SDK — the old name is silently ignored at runtime",
            "Replace '" + base + "' with '" + renamed + "' in " + field,
            List.of(renamed)));
        continue;
      }

      List<String> similar = FuzzyMatch.findSimilar(base, caps.knownToolNames());
      String hint = similar.isEmpty()
          ? "Use a built-in tool name, or the mcp__<server>__<tool> form for MCP tools"
          : "Did you mean: " + similar.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", "))
              + "? (MCP tools use the mcp__<server>__<tool> form)";
      findings.add(Finding.warning(
          field,
          "Unknown tool '" + base + "' for provider '" + caps.id()
              + "' — unrecognized names are silently ignored at runtime",
          hint,
          similar));
    }
  }

  /**
   * Start from the allow-list where one is given, otherwise from the provider's full
   * built-in set; subtract every denied name; keep first-seen order and drop duplicates.
   */
  public static EffectiveTools effective(
      List<String> allowed, List<String> denied, ProviderCapabilities caps) {
    if (allowed == null && denied == null) {
      return new EffectiveTools(null, List.of(), true);
    }

    // One index per call rather than a scan of the built-in list per name: both lists
    // come from a request, so the inner loop is caller-controlled.
    Map<String, String> index = new LinkedHashMap<>();
    for (String tool : caps.builtInTools()) {
      index.put(tool.toLowerCase(Locale.ROOT), tool);
    }

    List<String> unknown = new ArrayList<>();
    LinkedHashSet<String> selected = allowed != null
        ? normalise(allowed, index, unknown)
        : new LinkedHashSet<>(caps.builtInTools());

    if (denied != null) {
      selected.removeAll(normalise(denied, index, unknown));
    }

    return new EffectiveTools(List.copyOf(selected), List.copyOf(unknown), false);
  }

  /**
   * Names the provider knows, in the order written and without duplicates; anything else
   * is recorded and dropped.
   */
  private static LinkedHashSet<String> normalise(
      List<String> names, Map<String, String> index, List<String> unknown) {
    LinkedHashSet<String> kept = new LinkedHashSet<>();
    for (String name : names) {
      String match = index.get(name.toLowerCase(Locale.ROOT));
      if (match == null) {
        unknown.add(name);
      } else {
        kept.add(match);
      }
    }
    return kept;
  }
}
