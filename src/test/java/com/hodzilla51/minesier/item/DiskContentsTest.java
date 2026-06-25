package com.hodzilla51.minesier.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiskContentsTest {
  @Test
  void normalizesAbsoluteAndBackslashPaths() {
    assertEquals("startup.js", DiskContents.normalizePath("/startup.js"));
    assertEquals("lib/math.js", DiskContents.normalizePath("\\lib\\math.js"));
    assertEquals("data/scans.json", DiskContents.normalizePath(" data/scans.json/ "));
  }

  @Test
  void rejectsEmptyTraversalAndBrokenPaths() {
    assertNull(DiskContents.normalizePath(""));
    assertNull(DiskContents.normalizePath("/"));
    assertNull(DiskContents.normalizePath("a//b"));
    assertNull(DiskContents.normalizePath("../secret"));
    assertNull(DiskContents.normalizePath("a/./b"));
    assertNull(DiskContents.normalizePath("a/../b"));
  }

  @Test
  void withAndWithoutUseNormalizedPaths() {
    DiskContents contents = DiskContents.EMPTY.with("/data/scans.json", "[]");

    assertEquals("[]", contents.files().get("data/scans.json"));
    assertTrue(contents.without("\\data\\scans.json").files().isEmpty());
  }

  @Test
  void invalidMutationsReturnSameInstance() {
    DiskContents contents = DiskContents.EMPTY.with("valid.txt", "ok");

    assertSame(contents, contents.with("../bad", "no"));
    assertSame(contents, contents.without("../bad"));
    assertFalse(contents.files().containsKey("../bad"));
  }
}
