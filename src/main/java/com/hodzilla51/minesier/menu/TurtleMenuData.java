package com.hodzilla51.minesier.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Position and client GUI size used to construct a full-screen turtle menu on both sides. */
public record TurtleMenuData(BlockPos pos, int screenWidth, int screenHeight) {
  public static final StreamCodec<RegistryFriendlyByteBuf, TurtleMenuData> STREAM_CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          TurtleMenuData::pos,
          ByteBufCodecs.VAR_INT,
          TurtleMenuData::screenWidth,
          ByteBufCodecs.VAR_INT,
          TurtleMenuData::screenHeight,
          TurtleMenuData::new);
}
