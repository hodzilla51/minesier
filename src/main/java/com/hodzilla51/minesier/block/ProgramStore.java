package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.disk.DiskStorage;
import com.hodzilla51.minesier.disk.FileSystemProvider;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * A terminal block entity (computer or turtle) with two storage drives:
 *
 * <ul>
 *   <li><b>C:</b> – local storage, always available, backed by a per-device UUID at
 *       {@code <world>/minesier/computers/<device-uuid>/}
 *   <li><b>D:</b> – disk storage, only available when a disk item is inserted, backed by the disk's
 *       UUID at {@code <world>/minesier/disks/<disk-uuid>/}
 * </ul>
 *
 * <p>In-game file paths use Windows-style drive prefixes: {@code C:/startup.js}, {@code
 * D:/programs/net.js}. When no prefix is given, {@code C:} is assumed.
 */
public interface ProgramStore {
  /** The currently inserted disk, or {@link ItemStack#EMPTY} if none. */
  ItemStack getDisk();

  /** Sets the inserted disk (and marks dirty). */
  void setDisk(ItemStack disk);

  /** Marks the owning block entity dirty. */
  void markChanged();

  /** Current scrollback (used for transient status notes). */
  String getTranscript();

  /**
   * Returns the Minecraft world save root, or {@code null} on the client side or before placement.
   */
  Path worldDirectory();

  /**
   * Returns this device's UUID string (for local storage), assigning a fresh one on first call.
   * Implementations must persist this to NBT.
   */
  String ensureDeviceId();

  /**
   * Player-registered (JavaScript-backed) mounts, keyed by uppercase drive letter ({@code "N:"}).
   * Implementations hold the live, mutable map; it is session-scoped (cleared on world reload until
   * resident persistence re-mounts it). Built-in {@code C:}/{@code D:} are <em>not</em> in here.
   */
  Map<String, FileSystemProvider> dynamicMounts();

  // ── Drive accessors ───────────────────────────────────────────────────────

  /** Local storage (C:) – always available as long as the world directory is accessible. */
  default DiskStorage localStorage() {
    String id = ensureDeviceId();
    Path wd = worldDirectory();
    if (id == null || wd == null) return null;
    return new DiskStorage(wd.resolve("minesier/computers/" + id));
  }

  /** Disk storage (D:) – null when no disk is inserted or world directory unavailable. */
  default DiskStorage diskStorage() {
    Path wd = worldDirectory();
    if (wd == null) return null;
    String id = ensureDiskId();
    if (id == null) return null;
    return new DiskStorage(wd.resolve("minesier/disks/" + id));
  }

  default boolean hasDisk() {
    return !getDisk().isEmpty();
  }

  /** Gets or assigns a random UUID to the inserted disk item. Returns null if no disk. */
  default String ensureDiskId() {
    ItemStack disk = getDisk();
    if (disk.isEmpty()) return null;
    String id = disk.get(ModContent.DISK_ID);
    if (id != null && !id.isEmpty()) return id;
    String newId = UUID.randomUUID().toString();
    disk.set(ModContent.DISK_ID, newId);
    markChanged();
    return newId;
  }

  // ── Drive path parsing + mount resolution (VFS) ───────────────────────────

  /**
   * Splits a full path like {@code "C:/startup.js"} into {@code ["C:", "startup.js"]}, accepting
   * any drive letter ({@code C:}, {@code D:}, {@code N:}, …). The drive is upper-cased. A path
   * without a {@code "X:/"} prefix defaults to {@code C:}. Returns {@code null} for null input.
   */
  static String[] parseDrivePath(String fullPath) {
    if (fullPath == null) return null;
    int colon = fullPath.indexOf(':');
    if (colon > 0 && colon + 1 < fullPath.length() && fullPath.charAt(colon + 1) == '/') {
      String drive = fullPath.substring(0, colon + 1).toUpperCase(Locale.ROOT);
      return new String[] {drive, fullPath.substring(colon + 2)};
    }
    return new String[] {"C:", fullPath}; // no prefix → local
  }

  /**
   * Normalizes a player drive name to {@code "X:"} (single upper-case letter + colon), or null if
   * invalid or a reserved built-in ({@code C:}/{@code D:}).
   */
  static String canonicalDrive(String drive) {
    if (drive == null) return null;
    String d = drive.trim().toUpperCase(Locale.ROOT);
    if (d.endsWith("/")) d = d.substring(0, d.length() - 1);
    if (d.length() == 1) d = d + ":";
    if (d.length() != 2 || d.charAt(1) != ':' || d.charAt(0) < 'A' || d.charAt(0) > 'Z') {
      return null;
    }
    if ("C:".equals(d) || "D:".equals(d)) return null; // can't shadow built-ins
    return d;
  }

  /** Resolves a drive letter to its backing provider, or {@code null} if not mounted. */
  default FileSystemProvider providerFor(String drive) {
    if ("C:".equals(drive)) return localStorage();
    if ("D:".equals(drive)) return hasDisk() ? diskStorage() : null;
    return dynamicMounts().get(drive);
  }

  /**
   * All currently mounted drives in display order: {@code C:} first, then {@code D:} (only when a
   * disk is inserted), then player mounts in registration order. Null providers are skipped.
   */
  default Map<String, FileSystemProvider> mounts() {
    Map<String, FileSystemProvider> all = new LinkedHashMap<>();
    DiskStorage local = localStorage();
    if (local != null) all.put("C:", local);
    if (hasDisk()) {
      DiskStorage disk = diskStorage();
      if (disk != null) all.put("D:", disk);
    }
    all.putAll(dynamicMounts());
    return all;
  }

  // ── File operations ───────────────────────────────────────────────────────

  default boolean saveFile(String fullPath, String text) {
    String[] dp = parseDrivePath(fullPath);
    if (dp == null) return false;
    String normalized = DiskStorage.normalizePath(dp[1]);
    if (normalized == null) return false;
    FileSystemProvider provider = providerFor(dp[0]);
    return provider != null && provider.write(normalized, text);
  }

  default String readFile(String fullPath) {
    String[] dp = parseDrivePath(fullPath);
    if (dp == null) return null;
    String normalized = DiskStorage.normalizePath(dp[1]);
    if (normalized == null) return null;
    FileSystemProvider provider = providerFor(dp[0]);
    if (provider == null) return null;
    String text = provider.read(normalized);
    // .js fallback for files saved before the extension requirement
    if (text == null && !normalized.endsWith(".js")) {
      text = provider.read(normalized + ".js");
    }
    return text;
  }

  default boolean deleteFile(String fullPath) {
    String[] dp = parseDrivePath(fullPath);
    if (dp == null) return false;
    String normalized = DiskStorage.normalizePath(dp[1]);
    if (normalized == null) return false;
    FileSystemProvider provider = providerFor(dp[0]);
    return provider != null && provider.delete(normalized);
  }

  default boolean fileExists(String fullPath) {
    String[] dp = parseDrivePath(fullPath);
    if (dp == null) return false;
    String normalized = DiskStorage.normalizePath(dp[1]);
    if (normalized == null) return false;
    FileSystemProvider provider = providerFor(dp[0]);
    return provider != null && provider.exists(normalized);
  }

  /**
   * Returns all file paths across every mounted drive, prefixed with the drive letter (e.g. {@code
   * "C:/startup.js"}, {@code "D:/lib/net.js"}, {@code "N:/shared/config.json"}).
   */
  default Set<String> filePaths() {
    Set<String> result = new TreeSet<>();
    for (var entry : mounts().entrySet()) {
      Set<String> entries = entry.getValue().listAll();
      if (entries.isEmpty()) {
        // Bare drive root so an empty mounted drive still shows its header in the file tree.
        result.add(entry.getKey() + "/");
      } else {
        for (String p : entries) result.add(entry.getKey() + "/" + p);
      }
    }
    return result;
  }

  default Set<String> programNames() {
    return filePaths();
  }

  /** Creates a directory on the appropriate drive. {@code fullPath} may end with {@code /}. */
  default boolean mkdir(String fullPath) {
    String[] dp = parseDrivePath(fullPath);
    if (dp == null) return false;
    String dir = dp[1].endsWith("/") ? dp[1].substring(0, dp[1].length() - 1) : dp[1];
    String normalized = DiskStorage.normalizePath(dir);
    if (normalized == null) return false;
    FileSystemProvider provider = providerFor(dp[0]);
    return provider != null && provider.mkdir(normalized);
  }

  /** Lists names in a directory (files as-is, directories with trailing {@code /}). */
  default List<String> listFiles(String dirPath) {
    String[] dp = parseDrivePath(dirPath);
    if (dp == null) return List.of();
    FileSystemProvider provider = providerFor(dp[0]);
    if (provider == null) return List.of();
    String normalized =
        (dp[1] == null || dp[1].isBlank() || "/".equals(dp[1].trim()))
            ? ""
            : DiskStorage.normalizePath(dp[1]);
    if (normalized == null) return List.of();
    return provider.listDir(normalized);
  }

  // Legacy shims kept for internal callers
  default void saveProgram(String name, String source) { saveFile(name, source); }
  default String loadProgram(String name) { return readFile(name); }
  default void deleteProgram(String name) { deleteFile(name); }
}
