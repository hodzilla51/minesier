package com.hodzilla51.minesier.item;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Text files stored on a portable disk item. Paths are normalized without a leading slash. */
public record DiskContents(Map<String, String> files) {
  public static final DiskContents EMPTY = new DiskContents(Map.of());

  public static final Codec<DiskContents> CODEC =
      Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(DiskContents::new, DiskContents::files);

  public static final StreamCodec<RegistryFriendlyByteBuf, DiskContents> STREAM_CODEC =
      ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(
              HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)
          .map(DiskContents::new, DiskContents::files);

  public static String normalizePath(String path) {
    if (path == null) {
      return null;
    }
    String normalized = path.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isEmpty() || normalized.contains("\u0000")) {
      return null;
    }
    for (String part : normalized.split("/", -1)) {
      if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
        return null;
      }
    }
    return normalized;
  }

  /** Returns a copy with {@code path -> text} set, or this contents when the path is invalid. */
  public DiskContents with(String path, String text) {
    String normalized = normalizePath(path);
    if (normalized == null) {
      return this;
    }
    Map<String, String> next = new HashMap<>(files);
    next.put(normalized, text);
    return new DiskContents(Map.copyOf(next));
  }

  /** Returns a copy with {@code path} removed, or this contents when the path is invalid. */
  public DiskContents without(String path) {
    String normalized = normalizePath(path);
    if (normalized == null) {
      return this;
    }
    Map<String, String> next = new HashMap<>(files);
    next.remove(normalized);
    return new DiskContents(Map.copyOf(next));
  }
}
