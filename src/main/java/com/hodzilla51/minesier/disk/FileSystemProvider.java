package com.hodzilla51.minesier.disk;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * A mountable filesystem backend behind one drive letter (e.g. {@code C:}, {@code D:}, {@code N:}).
 *
 * <p>All paths are drive-relative and already normalized (no drive prefix, no leading/trailing
 * slash) — the VFS in {@link com.hodzilla51.minesier.block.ProgramStore} strips the {@code "X:/"}
 * prefix before dispatching here.
 *
 * <p>{@link DiskStorage} is the built-in disk-backed implementation (used for {@code C:}/{@code
 * D:}). Player programs can register their own implementations via {@code fs.mount(...)}; those are
 * backed by JavaScript callbacks, so a drive can be anything reachable from the sandbox (network
 * share, RAM disk, computed/virtual files, …).
 */
public interface FileSystemProvider {
  /** Reads a file's raw bytes, or {@code null} if it doesn't exist (or can't be read). */
  byte[] readBytes(String path);

  /** Writes raw bytes to a file, creating parents; returns whether it succeeded. */
  boolean writeBytes(String path, byte[] data);

  /**
   * Reads a file as UTF-8 text, or {@code null} if it doesn't exist. Convenience over {@link
   * #readBytes}; decoding is lenient (invalid bytes become U+FFFD). Callers that must distinguish
   * binary content from missing files should use {@link #readBytes} and decode strictly themselves.
   */
  default String read(String path) {
    byte[] bytes = readBytes(path);
    return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
  }

  /** Writes UTF-8 text to a file. Convenience over {@link #writeBytes}. */
  default boolean write(String path, String text) {
    return writeBytes(path, text.getBytes(StandardCharsets.UTF_8));
  }

  boolean delete(String path);

  boolean exists(String path);

  boolean mkdir(String path);

  /** Every entry relative to the drive root; empty directories end with a trailing {@code /}. */
  Set<String> listAll();

  /** One directory level; sub-directories end with a trailing {@code /}. */
  List<String> listDir(String path);
}
