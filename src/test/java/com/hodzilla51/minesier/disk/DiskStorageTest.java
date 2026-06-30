package com.hodzilla51.minesier.disk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskStorageTest {
  @Test
  void writeThenReadRoundTrips(@TempDir Path root) {
    DiskStorage disk = new DiskStorage(root);

    assertTrue(disk.write("startup.js", "print('hi')"));
    assertEquals("print('hi')", disk.read("startup.js"));
  }

  @Test
  void writeCreatesMissingParentDirectories(@TempDir Path root) {
    DiskStorage disk = new DiskStorage(root);

    assertTrue(disk.write("lib/net/frame.js", "module.exports = {}"));
    assertEquals("module.exports = {}", disk.read("lib/net/frame.js"));
  }

  @Test
  void overwriteReplacesContentAtomically(@TempDir Path root) throws IOException {
    DiskStorage disk = new DiskStorage(root);

    assertTrue(disk.write("data.json", "v1"));
    assertTrue(disk.write("data.json", "v2"));
    assertEquals("v2", disk.read("data.json"));

    // The atomic-rename write must not leave a stray temp file behind in the directory.
    try (Stream<Path> entries = Files.list(root)) {
      assertFalse(
          entries.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")),
          "no .tmp residue should remain after a successful write");
    }
  }

  @Test
  void rejectsPathEscapingRoot(@TempDir Path root) {
    DiskStorage disk = new DiskStorage(root);

    assertFalse(disk.write("../escape.txt", "no"));
  }
}
