package com.hodzilla51.minesier.js;

/**
 * Derives the short, human-readable name shown for a resident process from its source: the first
 * non-blank line, capped at 40 characters. Shared by Computer and Turtle so both label a running
 * program identically.
 */
public final class ProcessName {
  private static final int MAX = 40;

  private ProcessName() {}

  /** The display name for {@code source}, or an empty string when it is null/blank. */
  public static String describe(String source) {
    if (source == null) {
      return "";
    }
    String stripped = source.stripLeading();
    int newline = stripped.indexOf('\n');
    String first = (newline >= 0 ? stripped.substring(0, newline) : stripped).strip();
    return first.length() > MAX ? first.substring(0, MAX) + "…" : first;
  }
}
