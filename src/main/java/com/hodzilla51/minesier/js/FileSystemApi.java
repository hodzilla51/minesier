package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.disk.FileSystemProvider;
import java.util.List;

/**
 * Script-visible text filesystem. Routes drive-prefixed paths ({@code C:/}, {@code D:/}, and any
 * player-mounted drive) through the computer's VFS.
 */
public interface FileSystemApi {
  List<String> list(String path);

  String read(String path);

  boolean write(String path, String text);

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
