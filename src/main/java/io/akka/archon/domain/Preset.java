package io.akka.archon.domain;

/**
 * A named model choice: which provider, which model on it, and how deeply it should
 * reason. A preset carries its own provider, which is why resolving a model reference can
 * change which provider a node runs on.
 *
 * @param effort null where the preset states no reasoning depth
 */
public record Preset(String provider, String model, String effort) {

  public Preset {
    if (provider == null || provider.isEmpty()) {
      throw new IllegalArgumentException("preset has an invalid provider — must be a non-empty string");
    }
    if (model == null || model.isEmpty()) {
      throw new IllegalArgumentException("preset has an invalid model — must be a non-empty string");
    }
  }
}
