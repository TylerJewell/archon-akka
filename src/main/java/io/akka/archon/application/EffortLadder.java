package io.akka.archon.application;

import io.akka.archon.domain.ProviderCapabilities;

import java.util.List;

/**
 * How deep a run reasons: one vocabulary for every provider, two ways of refusing a rung,
 * and a clamp that lands a rung inside one provider's own SDK.
 */
public final class EffortLadder {

  /** The rungs, weakest to strongest. The order is load-bearing — {@link #clamp} walks it. */
  public static final List<String> LADDER =
      List.of("minimal", "low", "medium", "high", "xhigh", "max");

  /** Why a rung cannot be applied. {@code wire()} is the spelling the original uses. */
  public enum Reason {
    /** The provider has no reasoning control at all, so no rung would reach it. */
    UNSUPPORTED("unsupported"),
    /** The provider has one, but this is not a rung on the ladder. */
    UNKNOWN("unknown");

    private final String wire;

    Reason(String wire) {
      this.wire = wire;
    }

    public String wire() {
      return wire;
    }
  }

  /**
   * @param valid the rungs that would have been accepted, or null when there are none
   *     because the provider has no reasoning control
   */
  public record Verdict(boolean ok, Reason reason, List<String> valid) {}

  private EffortLadder() {}

  /**
   * The vocabulary reasoning depth reaches this provider through, or null where it does
   * not reach at all. A provider nobody registered gives the same answer as one with no
   * reasoning control: this decides whether a depth arrives, and an absent provider is one
   * no depth arrives at.
   */
  public static List<String> validFor(String provider) {
    ProviderCapabilities caps = Providers.find(provider);
    if (caps == null || !caps.effortControl()) {
      return null;
    }
    return LADDER;
  }

  /** Whether a preset's reasoning depth can be applied to the provider that resolved. */
  public static Verdict forPreset(String provider, String effort) {
    List<String> valid = validFor(provider);
    if (valid == null) {
      return new Verdict(false, Reason.UNSUPPORTED, null);
    }
    if (!valid.contains(effort)) {
      return new Verdict(false, Reason.UNKNOWN, valid);
    }
    return new Verdict(true, null, valid);
  }

  /**
   * Land a rung inside one provider's own vocabulary.
   *
   * <p>Down first, and not by distance: a clamp must never buy more reasoning than the
   * author asked for, so {@code high} against {@code [low, xhigh]} is {@code low} — two
   * rungs down — rather than {@code xhigh}, one rung up. Only when nothing weaker is
   * supported does it move up. A value off the ladder clamps to nothing and the caller
   * owns the warning.
   */
  public static String clamp(String value, List<String> supported) {
    int index = value == null ? -1 : LADDER.indexOf(value);
    if (index < 0) {
      return null;
    }
    if (supported.contains(value)) {
      return value;
    }
    for (int i = index - 1; i >= 0; i--) {
      if (supported.contains(LADDER.get(i))) {
        return LADDER.get(i);
      }
    }
    for (int i = index + 1; i < LADDER.size(); i++) {
      if (supported.contains(LADDER.get(i))) {
        return LADDER.get(i);
      }
    }
    return null;
  }
}
