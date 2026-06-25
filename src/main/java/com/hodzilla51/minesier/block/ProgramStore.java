package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.item.DiskContents;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.item.ItemStack;

/**
 * A terminal block entity (computer or turtle) whose saved programs live on an inserted disk. The
 * program data belongs to the disk {@link ItemStack} (a data component), so it travels with the
 * disk rather than being tied to this position.
 *
 * <p>Implementors only supply the disk slot + {@link #markChanged}; the program operations are
 * shared defaults that read/write the disk's component.
 */
public interface ProgramStore {
  /** The currently inserted disk, or {@link ItemStack#EMPTY} if none. */
  ItemStack getDisk();

  /** Sets the inserted disk (and marks dirty). */
  void setDisk(ItemStack disk);

  /** Marks the owning block entity dirty (so the disk slot is saved). */
  void markChanged();

  /** Current scrollback (used to push transient status notes back to the client). */
  String getTranscript();

  default boolean hasDisk() {
    return !getDisk().isEmpty();
  }

  default DiskContents contents() {
    DiskContents c = getDisk().get(ModContent.DISK_CONTENTS);
    return c == null ? DiskContents.EMPTY : c;
  }

  default void saveProgram(String name, String source) {
    saveFile(name, source);
  }

  default boolean saveFile(String path, String text) {
    String normalized = DiskContents.normalizePath(path);
    if (!hasDisk() || normalized == null) {
      return false;
    }
    getDisk().set(ModContent.DISK_CONTENTS, contents().with(normalized, text));
    markChanged();
    return true;
  }

  default String loadProgram(String name) {
    return readFile(name);
  }

  default String readFile(String path) {
    String normalized = DiskContents.normalizePath(path);
    return normalized == null ? null : contents().files().get(normalized);
  }

  default Set<String> programNames() {
    return filePaths();
  }

  default Set<String> filePaths() {
    return contents().files().keySet();
  }

  default void deleteProgram(String name) {
    deleteFile(name);
  }

  default boolean deleteFile(String path) {
    String normalized = DiskContents.normalizePath(path);
    if (!hasDisk() || normalized == null) {
      return false;
    }
    getDisk().set(ModContent.DISK_CONTENTS, contents().without(normalized));
    markChanged();
    return true;
  }

  default boolean fileExists(String path) {
    String normalized = DiskContents.normalizePath(path);
    return normalized != null && contents().files().containsKey(normalized);
  }

  default List<String> listFiles(String path) {
    String normalized =
        path == null || path.isBlank() || "/".equals(path.trim())
            ? ""
            : DiskContents.normalizePath(path);
    if (normalized == null) {
      return List.of();
    }
    String prefix = normalized.isEmpty() ? "" : normalized + "/";
    TreeSet<String> names = new TreeSet<>();
    for (String file : contents().files().keySet()) {
      if (!file.startsWith(prefix)) {
        continue;
      }
      String rest = file.substring(prefix.length());
      int slash = rest.indexOf('/');
      names.add(slash >= 0 ? rest.substring(0, slash + 1) : rest);
    }
    return List.copyOf(names);
  }
}
