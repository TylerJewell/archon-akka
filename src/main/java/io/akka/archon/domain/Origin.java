package io.akka.archon.domain;

/**
 * Where a resolved value came from, most specific first. {@link #UNSET} is a real answer:
 * a model nobody chose is not the same as a default one, and nothing downstream may treat
 * the two alike.
 *
 * <p>{@link #wire()} is the spelling coleam00/Archon uses for the same six values, so a
 * caller comparing the two systems is comparing answers rather than vocabularies.
 */
public enum Origin {
  NODE("node"),
  MODEL_REF("model ref"),
  WORKFLOW("workflow"),
  ASSISTANT_CONFIG("assistant config"),
  DEFAULT_ASSISTANT("default assistant"),
  UNSET("unset");

  private final String wire;

  Origin(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }
}
