package io.akka.archon.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SPEC-001 §3 rules 20-21 — the two gates a reasoning depth passes, and the clamp. */
class EffortLadderTest {

  /** Rule 20. */
  @Test
  void unsupportedAndUnknownAreDistinguishable() {
    var noControl = EffortLadder.forPreset("opencode", "high");
    assertThat(noControl.ok()).isFalse();
    assertThat(noControl.reason()).isEqualTo(EffortLadder.Reason.UNSUPPORTED);
    assertThat(noControl.valid()).isNull();

    var offLadder = EffortLadder.forPreset("claude", "ultra");
    assertThat(offLadder.ok()).isFalse();
    assertThat(offLadder.reason()).isEqualTo(EffortLadder.Reason.UNKNOWN);
    assertThat(offLadder.valid()).isEqualTo(EffortLadder.LADDER);

    assertThat(EffortLadder.forPreset("claude", "high").ok()).isTrue();

    // A provider nobody registered gives the same answer as one with no reasoning
    // control — the source does not distinguish them and neither does this.
    var unregistered = EffortLadder.forPreset("nosuchprovider", "high");
    assertThat(unregistered.reason()).isEqualTo(EffortLadder.Reason.UNSUPPORTED);
    assertThat(EffortLadder.validFor("nosuchprovider")).isNull();
    assertThat(EffortLadder.validFor("opencode")).isNull();
    assertThat(EffortLadder.validFor("claude")).isEqualTo(EffortLadder.LADDER);
  }

  /** Rule 21. */
  @Test
  void clampPrefersTheNearestWeakerRung() {
    // Two rungs down beats one rung up: a clamp never buys more reasoning than was asked for.
    assertThat(EffortLadder.clamp("high", List.of("low", "xhigh"))).isEqualTo("low");

    // Only when nothing weaker is supported does it move up.
    assertThat(EffortLadder.clamp("minimal", List.of("low", "medium"))).isEqualTo("low");

    // A supported rung passes through untouched.
    assertThat(EffortLadder.clamp("medium", List.of("low", "medium", "high"))).isEqualTo("medium");

    // Off the ladder entirely clamps to nothing; the caller owns the warning.
    assertThat(EffortLadder.clamp("ultra", List.of("low"))).isNull();
    assertThat(EffortLadder.clamp(null, List.of("low"))).isNull();

    // Against the real vocabularies the source exports.
    assertThat(EffortLadder.clamp("max", Providers.require("codex").supportedEfforts())).isEqualTo("xhigh");
    assertThat(EffortLadder.clamp("minimal", Providers.require("copilot").supportedEfforts())).isEqualTo("low");
    assertThat(EffortLadder.clamp("minimal", Providers.require("claude").supportedEfforts())).isEqualTo("low");
    assertThat(EffortLadder.clamp("max", Providers.require("pi").supportedEfforts())).isEqualTo("max");
  }
}
