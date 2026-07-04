package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises the text/binary boundary of the default methods on {@link FileSystemApi}. */
class FileSystemApiTest {
  /** Minimal in-memory FileSystemApi — only the byte primitives are real; text is default logic. */
  private static final class MemFs implements FileSystemApi {
    final Map<String, byte[]> files = new HashMap<>();

    @Override
    public List<String> list(String path) {
      return List.copyOf(files.keySet());
    }

    @Override
    public byte[] readBytes(String path) {
      return files.get(path);
    }

    @Override
    public boolean writeBytes(String path, byte[] data) {
      files.put(path, data);
      return true;
    }

    @Override
    public boolean remove(String path) {
      return files.remove(path) != null;
    }

    @Override
    public boolean exists(String path) {
      return files.containsKey(path);
    }
  }

  @Test
  void textReadReturnsNullForMissingButThrowsForBinary() {
    MemFs fs = new MemFs();
    fs.files.put("hello.txt", "hi".getBytes(StandardCharsets.UTF_8));
    fs.files.put("image.png", new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});

    assertEquals("hi", fs.read("hello.txt"));
    assertNull(fs.read("missing.txt"), "missing file must read as null");
    assertThrows(BinaryFileException.class, () -> fs.read("image.png"));

    // The inconsistency we set out to fix: exists() is true yet text read no longer silently nulls.
    assertTrue(fs.exists("image.png"));
  }

  @Test
  void byteRoundTripPreservesArbitraryBytes() {
    MemFs fs = new MemFs();
    byte[] blob = new byte[256];
    for (int i = 0; i < 256; i++) blob[i] = (byte) i;

    assertTrue(fs.writeBytes("d.bin", blob));
    assertArrayEquals(blob, fs.readBytes("d.bin"));
  }

  @Test
  void textWriteReplacesUnpairedSurrogatesWithU_FFFD() {
    MemFs fs = new MemFs();
    // A lone high surrogate (no following low surrogate) is a valid Java string but invalid
    // Unicode.
    assertTrue(fs.write("s.txt", "a\uD800b"));

    // U+FFFD encodes to EF BF BD in UTF-8 — no silent truncation or garbage.
    byte[] expected = "a�b".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(expected, fs.readBytes("s.txt"));
    assertEquals("a�b", fs.read("s.txt"));
  }

  @Test
  void bytesDecodeIsStrictAndEncodeIsLossySafe() {
    assertEquals("héllo", Bytes.decodeTextOrThrow("héllo".getBytes(StandardCharsets.UTF_8), "p"));
    assertThrows(
        BinaryFileException.class,
        () -> Bytes.decodeTextOrThrow(new byte[] {(byte) 0xC3, (byte) 0x28}, "p"));
    assertArrayEquals("a�b".getBytes(StandardCharsets.UTF_8), Bytes.encodeText("a\uD800b"));
  }
}
