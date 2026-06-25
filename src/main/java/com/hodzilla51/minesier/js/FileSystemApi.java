package com.hodzilla51.minesier.js;

import java.util.List;

/** Script-visible text filesystem backed by the currently inserted disk. */
public interface FileSystemApi {
  List<String> list(String path);

  String read(String path);

  boolean write(String path, String text);

  boolean remove(String path);

  boolean exists(String path);
}
