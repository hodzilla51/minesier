package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: open a credential prompt for one device. */
public record AccessPromptS2C(BlockPos pos, String mode) implements CustomPacketPayload {
  public static final Type<AccessPromptS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "access_prompt"));

  public static final StreamCodec<RegistryFriendlyByteBuf, AccessPromptS2C> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          AccessPromptS2C::pos,
          ByteBufCodecs.STRING_UTF8,
          AccessPromptS2C::mode,
          AccessPromptS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
