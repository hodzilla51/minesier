package com.hodzilla51.minesier.js;

/**
 * Thrown when a file is read as text but its bytes are not valid UTF-8 (e.g. a PNG or an encrypted
 * blob dropped into a disk folder by an external editor). Distinguishes "this file is binary" from
 * "this file does not exist" (which stays a {@code null} return), so a script can tell the two
 * apart and reach for {@link FileSystemApi#readBytes(String)} instead.
 */
public final class BinaryFileException extends RuntimeException {
  public BinaryFileException(String path) {
    super("not a text file (contains non-UTF-8 bytes): " + path + " — use fs.readBytes() instead");
  }
}
