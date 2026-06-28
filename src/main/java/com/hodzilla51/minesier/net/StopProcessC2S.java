package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: stop the resident process running at {@code pos}. */
public record StopProcessC2S(BlockPos pos) implements CustomPacketPayload {
  public static final Type<StopProcessC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "stop_process"));

  public static final StreamCodec<RegistryFriendlyByteBuf, StopProcessC2S> CODEC =
      StreamCodec.composite(BlockPos.STREAM_CODEC, StopProcessC2S::pos, StopProcessC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
