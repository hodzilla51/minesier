package com.hodzilla51.minesier.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: load this program source into the open terminal's editor. */
public record LoadProgramS2C(String source) implements CustomPacketPayload {
  public static final Type<LoadProgramS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "load_program"));

  public static final StreamCodec<RegistryFriendlyByteBuf, LoadProgramS2C> CODEC =
      StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LoadProgramS2C::source, LoadProgramS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
