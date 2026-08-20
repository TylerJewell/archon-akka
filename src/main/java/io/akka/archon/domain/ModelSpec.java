package io.akka.archon.domain;

/**
 * What a {@code model:} reference turned out to be. A reference is classified by its shape
 * alone: a tier keyword or an {@code @alias} resolves to a preset, and anything else is a
 * literal the provider is handed unchanged.
 */
public sealed interface ModelSpec {

  record FromPreset(Preset preset) implements ModelSpec {}

  record Literal(String value) implements ModelSpec {}
}
