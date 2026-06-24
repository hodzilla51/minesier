package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: a turtle completed a world action that has a short visual effect.
 *
 * <p>This packet intentionally contains no authoritative state. Its only purpose is to make the
 * turtle's renderer feel responsive without changing its block position or collision.
 */
public record TurtleVisualS2C(BlockPos pos, TurtleVisualAction action, String detail)
    implements CustomPacketPayload {
  public static final Type<TurtleVisualS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "turtle_visual"));

  public static final StreamCodec<RegistryFriendlyByteBuf, TurtleVisualS2C> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          TurtleVisualS2C::pos,
          ByteBufCodecs.STRING_UTF8,
          payload -> payload.action().name(),
          ByteBufCodecs.STRING_UTF8,
          TurtleVisualS2C::detail,
          (pos, action, detail) -> new TurtleVisualS2C(pos, parseAction(action), detail));

  private static TurtleVisualAction parseAction(String value) {
    try {
      return TurtleVisualAction.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return TurtleVisualAction.ERROR;
    }
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
