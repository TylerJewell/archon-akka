package io.akka.archon.application;

import io.akka.archon.domain.ModelSpec;
import io.akka.archon.domain.Preset;
import io.akka.archon.domain.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SPEC-001 §3 rules 10-14 and 22 — building a profile and classifying a model reference. */
class ModelProfileTest {

  /** Rule 10. */
  @Test
  void layersOverrideInOrder() {
    var withUser = ModelProfile.build(
        "claude",
        new ModelProfile.Layers(
            Map.of("large", new Preset("claude", "from-global", null)),
            Map.of("large", new Preset("claude", "from-repo", null)),
            Map.of("large", new Preset("claude", "from-user", null)),
            Map.of(),
            Map.of(),
            Map.of()));
    assertThat(withUser.presets().get("large").model()).isEqualTo("from-user");

    var withoutUser = ModelProfile.build(
        "claude",
        new ModelProfile.Layers(
            Map.of("large", new Preset("claude", "from-global", null)),
            Map.of("large", new Preset("claude", "from-repo", null)),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()));
    assertThat(withoutUser.presets().get("large").model()).isEqualTo("from-repo");

    // Nothing configured at all falls back to the built-in defaults for the provider.
    assertThat(ModelProfile.of("claude").presets().get("large").model()).isEqualTo("opus");
    assertThat(ModelProfile.of("codex").presets().get("small").effort()).isEqualTo("minimal");
  }

  /** Rule 11. */
  @Test
  void invalidDeclarationsAreRejected() {
    assertThatThrownBy(() -> ModelProfile.of("claude").withTier("enormous", new Preset("claude", "m", null)))
        .hasMessageContaining("Tier name 'enormous' is invalid");

    assertThatThrownBy(() -> ModelProfile.of("claude").withAlias("large", new Preset("claude", "m", null)))
        .hasMessageContaining("reserved");

    assertThatThrownBy(() -> ModelProfile.of("claude").withAlias("fast", new Preset("claude", "m", null)))
        .hasMessageContaining("must start with '@'");

    assertThatThrownBy(() -> ModelProfile.of("claude").withAlias("@f", new Preset("claude", "", null)))
        .hasMessageContaining("invalid model");

    assertThatThrownBy(() -> ModelProfile.of("claude").withAlias("@f", new Preset("", "m", null)))
        .hasMessageContaining("invalid provider");
  }

  /** Rule 12. */
  @Test
  void aReferenceIsClassifiedByShape() {
    var profile = ModelProfile.of("claude").withAlias("@fast", new Preset("claude", "haiku-x", null));

    assertThat(profile.resolve("large")).isEqualTo(new ModelSpec.FromPreset(new Preset("claude", "opus", null)));
    assertThat(profile.resolve("@fast")).isEqualTo(new ModelSpec.FromPreset(new Preset("claude", "haiku-x", null)));
    assertThat(profile.resolve("claude-opus-4-7")).isEqualTo(new ModelSpec.Literal("claude-opus-4-7"));
  }

  /** Rule 13. */
  @Test
  void tierFallbackChain() {
    var onlySmall = ModelProfile.empty("nosuchprovider").withTier("small", new Preset("claude", "s", null));
    assertThat(onlySmall.resolveTier(Tier.LARGE).matched()).isEqualTo(Tier.SMALL);

    var smallAndLarge = onlySmall.withTier("large", new Preset("claude", "l", null));
    assertThat(smallAndLarge.resolveTier(Tier.MEDIUM).matched()).isEqualTo(Tier.LARGE);
    assertThat(smallAndLarge.resolveTier(Tier.LARGE).matched()).isEqualTo(Tier.LARGE);

    var onlyMedium = ModelProfile.empty("nosuchprovider").withTier("medium", new Preset("claude", "m", null));
    assertThat(onlyMedium.resolveTier(Tier.SMALL).matched()).isEqualTo(Tier.MEDIUM);
  }

  /** Rule 14. */
  @Test
  void unresolvableReferencesAreRejected() {
    assertThatThrownBy(() -> ModelProfile.empty("nosuchprovider").resolveTier(Tier.SMALL))
        .hasMessageContaining("Tier 'small' has no configured preset")
        .hasMessageContaining("nosuchprovider");

    // A profile can be non-empty and still have nothing on the chain: aliases and tiers
    // share one map, and an alias key can never be a tier keyword.
    assertThatThrownBy(() -> ModelProfile.empty("nosuchprovider")
        .withAlias("@only", new Preset("claude", "m", null))
        .resolveTier(Tier.LARGE))
        .hasMessageContaining("Tier 'large' has no configured preset");

    assertThatThrownBy(() -> ModelProfile.of("claude").resolve("@nope"))
        .hasMessageContaining("Unknown alias '@nope'")
        .hasMessageContaining("small, medium, large");
  }

  /** Rule 22. */
  @Test
  void aTierKeywordIsATierOnlyAsTheWholeReference() {
    assertThat(Tier.of("large")).isEqualTo(Tier.LARGE);
    assertThat(Tier.of("@large")).isNull();
    assertThat(Tier.of("opus")).isNull();
    assertThat(Tier.of("large-ish")).isNull();
  }
}
