package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: open the expansion-parts menu for the turtle at {@code pos}. */
public record OpenTurtleEquipmentC2S(BlockPos pos, int screenWidth, int screenHeight)
    implements CustomPacketPayload {
  public static final Type<OpenTurtleEquipmentC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "open_turtle_equipment"));

  public static final StreamCodec<RegistryFriendlyByteBuf, OpenTurtleEquipmentC2S> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          OpenTurtleEquipmentC2S::pos,
          ByteBufCodecs.VAR_INT,
          OpenTurtleEquipmentC2S::screenWidth,
          ByteBufCodecs.VAR_INT,
          OpenTurtleEquipmentC2S::screenHeight,
          OpenTurtleEquipmentC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
