package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.disk.FileSystemProvider;
import java.util.List;

/**
 * Script-visible text filesystem. Routes drive-prefixed paths ({@code C:/}, {@code D:/}, and any
 * player-mounted drive) through the computer's VFS.
 */
public interface FileSystemApi {
  List<String> list(String path);

  /** Raw bytes of a file, or {@code null} if it doesn't exist. */
  byte[] readBytes(String path);

  /** Writes raw bytes to a file; returns whether it succeeded. */
  boolean writeBytes(String path, byte[] data);

  /**
   * Reads a file as UTF-8 text. Returns {@code null} when the file <em>doesn't exist</em>, but
   * throws {@link BinaryFileException} when the file exists yet isn't valid UTF-8 — so a script can
   * tell "no such file" (null) apart from "binary file" (exception) instead of both looking empty.
   */
  default String read(String path) {
    byte[] bytes = readBytes(path);
    return bytes == null ? null : Bytes.decodeTextOrThrow(bytes, path);
  }

  /** Writes UTF-8 text (unpaired surrogates → U+FFFD, never silent corruption). */
  default boolean write(String path, String text) {
    return writeBytes(path, Bytes.encodeText(text));
  }

  boolean remove(String path);

  boolean exists(String path);

  /**
   * Registers a player-defined drive backed by {@code provider}. Built-in C:/D: can't be replaced.
   */
  default boolean mount(String drive, FileSystemProvider provider) {
    return false;
  }

  /** Removes a previously player-mounted drive. */
  default boolean unmount(String drive) {
    return false;
  }

  /** Currently mounted drive letters (e.g. {@code ["C:", "D:", "N:"]}). */
  default List<String> mounts() {
    return List.of();
  }
}
