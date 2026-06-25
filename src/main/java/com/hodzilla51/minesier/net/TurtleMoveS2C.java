package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: a turtle just hopped to {@code pos}, arriving from {@code fromDir} (the {@link
 * net.minecraft.core.Direction} ordinal pointing back toward where it came). The client uses this
 * to slide the turtle smoothly into place over the move's duration.
 */
public record TurtleMoveS2C(BlockPos pos, int fromDir, int durationTicks)
    implements CustomPacketPayload {
  public static final Type<TurtleMoveS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "turtle_move"));

  public static final StreamCodec<RegistryFriendlyByteBuf, TurtleMoveS2C> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          TurtleMoveS2C::pos,
          ByteBufCodecs.VAR_INT,
          TurtleMoveS2C::fromDir,
          ByteBufCodecs.VAR_INT,
          TurtleMoveS2C::durationTicks,
          TurtleMoveS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
