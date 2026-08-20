package io.akka.archon.domain;

import java.util.List;

/**
 * The three reserved size keywords a {@code model:} reference may be, and the order each
 * one settles for when it is not configured.
 *
 * <p>A keyword is a tier only where it is the whole reference — {@code @large} is an alias
 * name and {@code large-ish} is a literal model string.
 */
public enum Tier {
  SMALL("small"),
  MEDIUM("medium"),
  LARGE("large");

  private final String wire;

  Tier(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }

  /** The tier this name denotes, or {@code null} where the name is not a tier at all. */
  public static Tier of(String name) {
    for (Tier tier : values()) {
      if (tier.wire.equals(name)) {
        return tier;
      }
    }
    return null;
  }

  /**
   * Where an unconfigured tier looks next. MEDIUM prefers the over-capable LARGE to SMALL:
   * a near miss in capability beats an unrelated tier, in the direction that does not
   * quietly weaken what was asked for.
   */
  public List<Tier> fallbackChain() {
    return switch (this) {
      case LARGE -> List.of(LARGE, MEDIUM, SMALL);
      case MEDIUM -> List.of(MEDIUM, LARGE, SMALL);
      case SMALL -> List.of(SMALL, MEDIUM, LARGE);
    };
  }
}
