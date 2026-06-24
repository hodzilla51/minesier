package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: select, insert into, or extract from a Turtle inventory slot. */
public record TurtleInventoryActionC2S(BlockPos pos, int action, int slot)
    implements CustomPacketPayload {
  public static final int SELECT = 0;
  public static final int INSERT_HELD = 1;
  public static final int EXTRACT = 2;

  public static final Type<TurtleInventoryActionC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "turtle_inventory_action"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TurtleInventoryActionC2S> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          TurtleInventoryActionC2S::pos,
          ByteBufCodecs.VAR_INT,
          TurtleInventoryActionC2S::action,
          ByteBufCodecs.VAR_INT,
          TurtleInventoryActionC2S::slot,
          TurtleInventoryActionC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
