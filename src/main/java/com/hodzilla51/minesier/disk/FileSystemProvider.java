package com.hodzilla51.minesier.disk;

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
  String read(String path);

  boolean write(String path, String text);

  boolean delete(String path);

  boolean exists(String path);

  boolean mkdir(String path);

  /** Every entry relative to the drive root; empty directories end with a trailing {@code /}. */
  Set<String> listAll();

  /** One directory level; sub-directories end with a trailing {@code /}. */
  List<String> listDir(String path);
}
