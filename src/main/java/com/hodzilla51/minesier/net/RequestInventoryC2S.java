package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: please send the current inventory snapshot of the turtle at {@code pos}. */
public record RequestInventoryC2S(BlockPos pos) implements CustomPacketPayload {
	public static final Type<RequestInventoryC2S> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("minesier", "request_inventory"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RequestInventoryC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, RequestInventoryC2S::pos,
		RequestInventoryC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
