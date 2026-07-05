package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The shared resident-process name derivation used by both Computer and Turtle terminals. */
class ProcessNameTest {

  @Test
  void nullAndBlankSourceHaveNoName() {
    assertEquals("", ProcessName.describe(null));
    assertEquals("", ProcessName.describe(""));
    assertEquals("", ProcessName.describe("   \n  "));
  }

  @Test
  void firstNonBlankLineIsTheName() {
    assertEquals("every(20, tick)", ProcessName.describe("every(20, tick)\nmore();"));
    assertEquals("second", ProcessName.describe("\n\n  second\nthird"));
  }

  @Test
  void longNamesAreCappedWithEllipsis() {
    String source = "x".repeat(60);
    String name = ProcessName.describe(source);
    assertEquals("x".repeat(40) + "…", name);
    assertEquals(41, name.length(), "40 chars plus the ellipsis");
  }
}
