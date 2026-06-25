package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: read-only status text for a managed switch block. */
public record SwitchStatusS2C(BlockPos pos, String status) implements CustomPacketPayload {
  public static final Type<SwitchStatusS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "switch_status"));

  public static final StreamCodec<RegistryFriendlyByteBuf, SwitchStatusS2C> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          SwitchStatusS2C::pos,
          ByteBufCodecs.STRING_UTF8,
          SwitchStatusS2C::status,
          SwitchStatusS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
