package com.hodzilla51.minesier.disk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskStorageTest {
  /** A PNG signature plus bytes that are not valid UTF-8 — the classic external-editor case. */
  private static final byte[] BINARY = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, (byte) 0xFF, (byte) 0xFE, 0x00, (byte) 0xC0
  };

  @Test
  void writeBytesThenReadBytesRoundTrips(@TempDir Path root) {
    DiskStorage disk = new DiskStorage(root);

    assertTrue(disk.writeBytes("key.bin", BINARY));
    assertArrayEquals(BINARY, disk.readBytes("key.bin"));
  }

  @Test
  void readsBinaryPlacedByAnExternalEditor(@TempDir Path root) throws IOException {
    Files.write(root.resolve("image.png"), BINARY);

    DiskStorage disk = new DiskStorage(root);
    assertTrue(disk.exists("image.png"));
    assertArrayEquals(BINARY, disk.readBytes("image.png"));
  }

  @Test
  void readBytesReturnsNullForMissingFile(@TempDir Path root) {
    assertNull(new DiskStorage(root).readBytes("nope.bin"));
  }

  @Test
  void binaryWriteIsAtomicWithNoTempResidue(@TempDir Path root) throws IOException {
    DiskStorage disk = new DiskStorage(root);

    assertTrue(disk.writeBytes("blob", BINARY));
    try (Stream<Path> entries = Files.list(root)) {
      assertFalse(entries.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
    }
  }

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
