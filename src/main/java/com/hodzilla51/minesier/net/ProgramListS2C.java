package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: the names of the programs saved on the inserted disk (newline-joined, empty
 * when no disk), so the terminal can show a persistent file tree.
 */
public record ProgramListS2C(BlockPos pos, String names) implements CustomPacketPayload {
  public static final Type<ProgramListS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "program_list"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ProgramListS2C> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          ProgramListS2C::pos,
          ByteBufCodecs.STRING_UTF8,
          ProgramListS2C::names,
          ProgramListS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
