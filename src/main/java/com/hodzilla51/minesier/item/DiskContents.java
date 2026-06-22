package com.hodzilla51.minesier.item;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The data a disk holds: named program files. Stored as a data component on the disk
 * {@code ItemStack}, so it travels with the item — the data belongs to the medium,
 * not to any block position.
 */
public record DiskContents(Map<String, String> files) {
	public static final DiskContents EMPTY = new DiskContents(Map.of());

	public static final Codec<DiskContents> CODEC =
		Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(DiskContents::new, DiskContents::files);

	public static final StreamCodec<RegistryFriendlyByteBuf, DiskContents> STREAM_CODEC =
		ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(
				HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)
			.map(DiskContents::new, DiskContents::files);

	/** Returns a copy with {@code name -> source} set. */
	public DiskContents with(String name, String source) {
		Map<String, String> next = new HashMap<>(files);
		next.put(name, source);
		return new DiskContents(Map.copyOf(next));
	}

	/** Returns a copy with {@code name} removed. */
	public DiskContents without(String name) {
		Map<String, String> next = new HashMap<>(files);
		next.remove(name);
		return new DiskContents(Map.copyOf(next));
	}
}
