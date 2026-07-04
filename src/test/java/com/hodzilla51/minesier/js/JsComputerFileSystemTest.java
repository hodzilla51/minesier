package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Drives a real {@link JsComputer} VM to exercise the Rhino-specific parts of the byte filesystem:
 * {@code Uint8Array} marshalling in both directions, and the null-vs-throw error distinction that
 * only surfaces once a script actually calls {@code fs.read}/{@code fs.readBytes}.
 */
class JsComputerFileSystemTest {

  @BeforeAll
  static void installFactory() {
    SafeContextFactory.install();
  }

  /** Flat in-memory FileSystemApi keyed by the raw path string. */
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
  void writeBytesAndReadBytesRoundTripThroughUint8Array() {
    JsComputer computer = new JsComputer();
    MemFs fs = new MemFs();
    computer.setFileSystem(fs);

    List<String> out =
        computer.run(
            "var a = new Uint8Array([0, 127, 255, 10]);"
                + "fs.writeBytes('C:/k.bin', a);"
                + "var b = fs.readBytes('C:/k.bin');"
                + "print(b.length + ':' + b[0] + ',' + b[1] + ',' + b[2] + ',' + b[3]);");

    assertTrue(out.contains("4:0,127,255,10"), () -> "script output: " + out);
    // The Java side stored the exact bytes (255 → -1 as a signed byte).
    assertArrayEquals(new byte[] {0, 127, (byte) 255, 10}, fs.files.get("C:/k.bin"));
  }

  @Test
  void scriptCanTellMissingFileFromBinaryFile() {
    JsComputer computer = new JsComputer();
    MemFs fs = new MemFs();
    computer.setFileSystem(fs);
    fs.files.put("C:/img.png", new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});

    List<String> out =
        computer.run(
            "var r;"
                + "try { fs.read('C:/img.png'); r = 'no-throw'; }"
                + "catch (e) { r = 'threw'; }"
                + "print(r + '/' + (fs.read('C:/missing') === null));");

    // Binary text-read throws (catchable); a missing file still reads as null.
    assertTrue(out.contains("threw/true"), () -> "script output: " + out);
  }

  @Test
  void readBytesOfMissingFileIsNull() {
    JsComputer computer = new JsComputer();
    computer.setFileSystem(new MemFs());

    List<String> out = computer.run("print(fs.readBytes('C:/nope') === null);");

    assertTrue(out.contains("true"), () -> "script output: " + out);
  }
}
