package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: open the storage menu for the turtle at {@code pos}. */
public record OpenTurtleInventoryC2S(BlockPos pos, int screenWidth, int screenHeight)
    implements CustomPacketPayload {
  public static final Type<OpenTurtleInventoryC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "open_turtle_inventory"));

  public static final StreamCodec<RegistryFriendlyByteBuf, OpenTurtleInventoryC2S> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          OpenTurtleInventoryC2S::pos,
          ByteBufCodecs.VAR_INT,
          OpenTurtleInventoryC2S::screenWidth,
          ByteBufCodecs.VAR_INT,
          OpenTurtleInventoryC2S::screenHeight,
          OpenTurtleInventoryC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
