package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: run {@code command} on the computer at {@code pos}. */
public record RunCommandC2S(BlockPos pos, String command) implements CustomPacketPayload {
	public static final Type<RunCommandC2S> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("minesier", "run_command"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RunCommandC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, RunCommandC2S::pos,
		ByteBufCodecs.STRING_UTF8, RunCommandC2S::command,
		RunCommandC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
