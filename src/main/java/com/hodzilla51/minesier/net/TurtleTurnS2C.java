package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: the turtle at {@code pos} just turned. {@code clockwise} tells the
 * client which way, so it can smoothly ease the model from its previous heading into
 * the new (already-applied) facing.
 */
public record TurtleTurnS2C(BlockPos pos, boolean clockwise) implements CustomPacketPayload {
	public static final Type<TurtleTurnS2C> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("minesier", "turtle_turn"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TurtleTurnS2C> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TurtleTurnS2C::pos,
		ByteBufCodecs.BOOL, TurtleTurnS2C::clockwise,
		TurtleTurnS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
