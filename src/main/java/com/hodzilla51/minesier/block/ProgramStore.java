package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.item.DiskContents;
import java.util.Set;
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
    getDisk().set(ModContent.DISK_CONTENTS, contents().with(name, source));
    markChanged();
  }

  default String loadProgram(String name) {
    return contents().files().get(name);
  }

  default Set<String> programNames() {
    return contents().files().keySet();
  }

  default void deleteProgram(String name) {
    getDisk().set(ModContent.DISK_CONTENTS, contents().without(name));
    markChanged();
  }
}
