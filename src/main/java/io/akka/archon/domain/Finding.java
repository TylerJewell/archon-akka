package io.akka.archon.domain;

import java.util.List;

/**
 * Something an author should know about their own configuration. Never a decision: a
 * finding says what the runtime will do with a name it does not recognise, and leaves the
 * caller to decide whether that is fatal.
 *
 * @param hint null where the message is the whole of it
 * @param suggestions empty where nothing was near enough to suggest
 */
public record Finding(Level level, String field, String message, String hint, List<String> suggestions) {

  public Finding {
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
  }

  public static Finding warning(String field, String message, String hint, List<String> suggestions) {
    return new Finding(Level.WARNING, field, message, hint, suggestions);
  }
}
