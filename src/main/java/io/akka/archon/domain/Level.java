package io.akka.archon.domain;

/** How much a finding is allowed to stop. */
public enum Level {
  WARNING("warning"),
  ERROR("error");

  private final String wire;

  Level(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }
}
