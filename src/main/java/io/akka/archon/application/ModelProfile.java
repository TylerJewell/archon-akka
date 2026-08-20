package io.akka.archon.application;

import io.akka.archon.domain.ModelSpec;
import io.akka.archon.domain.Preset;
import io.akka.archon.domain.Tier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The named model choices one installation has, and how a {@code model:} reference is
 * turned into one of them.
 *
 * <p>Tier keywords and {@code @aliases} share a single map, which is why the error for an
 * unknown alias lists tiers among the defined names.
 *
 * <p>The profile is built from layers handed in, never read from disk here. That is what
 * makes a pinning reproducible: the same profile and the same node always resolve the same
 * way, and a profile is an argument rather than ambient state.
 */
public record ModelProfile(String defaultProvider, Map<String, Preset> presets) {

  /** What matched, and which tier it actually was — the two differ when a tier fell back. */
  public record TierMatch(Preset preset, Tier matched) {}

  /**
   * Configuration layers, weakest first. Each is a map from name to preset; a later layer
   * overrides an earlier one on the same key. Within one layer the entries are applied in
   * that map's own iteration order, so a caller who cares which order the resulting
   * presets are listed in hands over an ordered map.
   */
  public record Layers(
      Map<String, Preset> globalTiers,
      Map<String, Preset> repoTiers,
      Map<String, Preset> userTiers,
      Map<String, Preset> globalAliases,
      Map<String, Preset> repoAliases,
      Map<String, Preset> userAliases) {}

  /** Built-in tier presets per provider, the weakest layer of all. */
  private static final Map<String, Map<Tier, Preset>> TIER_DEFAULTS = Map.of(
      "claude", Map.of(
          Tier.SMALL, new Preset("claude", "haiku", null),
          Tier.MEDIUM, new Preset("claude", "sonnet", null),
          Tier.LARGE, new Preset("claude", "opus", null)),
      "codex", Map.of(
          Tier.SMALL, new Preset("codex", "gpt-5.5", "minimal"),
          Tier.MEDIUM, new Preset("codex", "gpt-5.5", "medium"),
          Tier.LARGE, new Preset("codex", "gpt-5.5", "high")),
      "pi", Map.of(
          Tier.SMALL, new Preset("pi", "anthropic/claude-haiku-4-5", null),
          Tier.MEDIUM, new Preset("pi", "anthropic/claude-sonnet-4-6", null),
          Tier.LARGE, new Preset("pi", "anthropic/claude-opus-4-7", null)),
      "copilot", Map.of(
          Tier.SMALL, new Preset("copilot", "gpt-5-mini", null),
          Tier.MEDIUM, new Preset("copilot", "gpt-5", null),
          Tier.LARGE, new Preset("copilot", "claude-sonnet-4.5", null)),
      "opencode", Map.of(
          Tier.SMALL, new Preset("opencode", "anthropic/claude-haiku-4-5", null),
          Tier.MEDIUM, new Preset("opencode", "anthropic/claude-sonnet-4-6", null),
          Tier.LARGE, new Preset("opencode", "anthropic/claude-opus-4-7", null)));

  public ModelProfile {
    // Insertion-ordered, not Map.copyOf: the order presets were defined in is what the
    // unknown-alias error lists them in, and an unordered copy would make that message
    // vary between runs of the same program.
    presets = Collections.unmodifiableMap(new LinkedHashMap<>(presets));
  }

  /** The built-in defaults for a provider, and nothing else. */
  public static ModelProfile of(String defaultProvider) {
    Map<String, Preset> presets = new LinkedHashMap<>();
    Map<Tier, Preset> defaults = TIER_DEFAULTS.get(defaultProvider);
    if (defaults != null) {
      for (Tier tier : Tier.values()) {
        Preset preset = defaults.get(tier);
        if (preset != null) {
          presets.put(tier.wire(), preset);
        }
      }
    }
    return new ModelProfile(defaultProvider, presets);
  }

  /** No presets at all — a provider with neither built-in defaults nor configuration. */
  public static ModelProfile empty(String defaultProvider) {
    return new ModelProfile(defaultProvider, Map.of());
  }

  /** Built-in defaults with every configured layer applied over them, weakest first. */
  public static ModelProfile build(String defaultProvider, Layers layers) {
    // The map is copied once at the end rather than once per entry: a layer is
    // caller-sized, and every intermediate profile would be thrown away.
    Map<String, Preset> presets = new LinkedHashMap<>(of(defaultProvider).presets());
    for (Map<String, Preset> tierLayer :
        List.of(layers.globalTiers(), layers.repoTiers(), layers.userTiers())) {
      for (Map.Entry<String, Preset> entry : tierLayer.entrySet()) {
        assertTierName(entry.getKey());
        presets.put(entry.getKey(), entry.getValue());
      }
    }
    for (Map<String, Preset> aliasLayer :
        List.of(layers.globalAliases(), layers.repoAliases(), layers.userAliases())) {
      for (Map.Entry<String, Preset> entry : aliasLayer.entrySet()) {
        assertAliasName(entry.getKey());
        presets.put(entry.getKey(), entry.getValue());
      }
    }
    return new ModelProfile(defaultProvider, presets);
  }

  /** Override one tier. The name must be one of the three keywords. */
  public ModelProfile withTier(String name, Preset preset) {
    assertTierName(name);
    return plus(name, preset);
  }

  /** Define one custom alias. The name must begin {@code @} and must not be a tier keyword. */
  public ModelProfile withAlias(String name, Preset preset) {
    assertAliasName(name);
    return plus(name, preset);
  }

  private static void assertTierName(String name) {
    if (Tier.of(name) == null) {
      throw new IllegalArgumentException(
          "Tier name '" + name + "' is invalid. Supported tiers: small, medium, large.");
    }
  }

  private static void assertAliasName(String name) {
    if (Tier.of(name) != null) {
      throw new IllegalArgumentException(
          "Alias name '" + name + "' is reserved (small/medium/large are tier keywords). Use a different name.");
    }
    if (!name.startsWith("@")) {
      throw new IllegalArgumentException(
          "Alias name '" + name + "' must start with '@' (e.g. '@" + name
              + "'). Reserved tier names (small/medium/large) do not need '@'.");
    }
  }

  private ModelProfile plus(String name, Preset preset) {
    Map<String, Preset> next = new LinkedHashMap<>(presets);
    next.put(name, preset);
    return new ModelProfile(defaultProvider, next);
  }

  /** Classify a reference by its shape and resolve it. */
  public ModelSpec resolve(String ref) {
    Tier tier = Tier.of(ref);
    if (tier != null) {
      return new ModelSpec.FromPreset(resolveTier(tier).preset());
    }
    if (ref.startsWith("@")) {
      Preset preset = presets.get(ref);
      if (preset != null) {
        return new ModelSpec.FromPreset(preset);
      }
      String defined = presets.isEmpty() ? "(none)" : String.join(", ", presets.keySet());
      throw new IllegalArgumentException("Unknown alias '" + ref + "'. Defined aliases: " + defined);
    }
    return new ModelSpec.Literal(ref);
  }

  /** Walk the tier's fallback chain and report which tier actually matched. */
  public TierMatch resolveTier(Tier tier) {
    for (Tier candidate : tier.fallbackChain()) {
      Preset preset = presets.get(candidate.wire());
      if (preset != null) {
        return new TierMatch(preset, candidate);
      }
    }
    throw new IllegalArgumentException(
        "Tier '" + tier.wire() + "' has no configured preset and no built-in default for provider '"
            + defaultProvider + "'. Configure 'tiers.small/medium/large' in .archon/config.yaml.");
  }
}
