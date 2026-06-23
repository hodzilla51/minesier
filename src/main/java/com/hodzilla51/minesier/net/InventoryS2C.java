package com.hodzilla51.minesier.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: a read-only snapshot of a turtle's inventory for the viewer.
 *
 * @param selected the selected slot index (0-based)
 * @param slots    one line per slot, each "itemId count" (or empty string for an empty slot)
 */
public record InventoryS2C(int selected, String slots) implements CustomPacketPayload {
	public static final Type<InventoryS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("minesier", "inventory"));

	public static final StreamCodec<RegistryFriendlyByteBuf, InventoryS2C> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, InventoryS2C::selected,
		ByteBufCodecs.STRING_UTF8, InventoryS2C::slots,
		InventoryS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
