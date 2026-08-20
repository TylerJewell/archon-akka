package io.akka.archon.domain;

import java.util.List;
import java.util.Map;

/**
 * What one provider can actually honour, which decides whether a field an author wrote
 * means anything at all.
 *
 * @param knownToolNames the vocabulary a tool name is checked against, or null where the
 *     provider declares none — a provider without one is not checked rather than having
 *     every name flagged
 * @param renamedTools old name to current name, for names whose meaning moved and whose
 *     old spelling is now ignored in silence
 * @param builtInTools the full set a denial subtracts from when no allow-list is given
 * @param defaultTools what the provider runs with when neither field is set
 * @param supportedEfforts the reasoning rungs this provider's own SDK offers, empty where
 *     it has no reasoning control
 */
public record ProviderCapabilities(
    String id,
    boolean toolRestrictions,
    boolean effortControl,
    List<String> knownToolNames,
    Map<String, String> renamedTools,
    List<String> builtInTools,
    List<String> defaultTools,
    List<String> supportedEfforts) {

  public ProviderCapabilities {
    knownToolNames = knownToolNames == null ? null : List.copyOf(knownToolNames);
    renamedTools = Map.copyOf(renamedTools);
    builtInTools = List.copyOf(builtInTools);
    defaultTools = List.copyOf(defaultTools);
    supportedEfforts = List.copyOf(supportedEfforts);
  }
}
