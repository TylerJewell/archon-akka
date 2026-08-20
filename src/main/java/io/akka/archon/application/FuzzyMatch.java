package io.akka.archon.application;

import java.util.Comparator;
import java.util.List;

/**
 * Which known names a misspelling was probably reaching for. Nothing here decides
 * anything — a suggestion is offered alongside a finding that stands on its own.
 */
public final class FuzzyMatch {

  private static final int MAX_SUGGESTIONS = 3;

  private FuzzyMatch() {}

  /** Classic Levenshtein distance. */
  public static int levenshtein(String a, String b) {
    int m = a.length();
    int n = b.length();
    int[] previous = new int[n + 1];
    int[] current = new int[n + 1];

    for (int j = 0; j <= n; j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= m; i++) {
      current[0] = i;
      for (int j = 1; j <= n; j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[n];
  }

  /**
   * The nearest candidates, nearest first, at most three. Comparison is
   * case-insensitive, an exact match suggests nothing, and the threshold grows with the
   * length of the name being matched.
   */
  public static List<String> findSimilar(String name, List<String> candidates) {
    int threshold = Math.max(2, (int) Math.floor(name.length() * 0.3));
    String lower = name.toLowerCase();
    record Scored(String name, int distance) {}
    return candidates.stream()
        .map(c -> new Scored(c, levenshtein(lower, c.toLowerCase())))
        .filter(s -> s.distance() <= threshold && s.distance() > 0)
        .sorted(Comparator.comparingInt(Scored::distance))
        .limit(MAX_SUGGESTIONS)
        .map(Scored::name)
        .toList();
  }
}
