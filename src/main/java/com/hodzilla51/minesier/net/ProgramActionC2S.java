package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: manage saved programs on the terminal at {@code pos}. {@code action}: 0=save,
 * 1=load, 2=list, 3=delete, 4=eject, 5=rename. {@code source} is used only by save; {@code
 * newName} is used only by rename.
 */
public record ProgramActionC2S(BlockPos pos, int action, String name, String source, String newName)
    implements CustomPacketPayload {
  public static final int SAVE = 0;
  public static final int LOAD = 1;
  public static final int LIST = 2;
  public static final int DELETE = 3;
  public static final int EJECT = 4;
  public static final int RENAME = 5;
  public static final int MKDIR  = 6;

  public static final Type<ProgramActionC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "program_action"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ProgramActionC2S> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          ProgramActionC2S::pos,
          ByteBufCodecs.VAR_INT,
          ProgramActionC2S::action,
          ByteBufCodecs.STRING_UTF8,
          ProgramActionC2S::name,
          ByteBufCodecs.STRING_UTF8,
          ProgramActionC2S::source,
          ByteBufCodecs.STRING_UTF8,
          ProgramActionC2S::newName,
          ProgramActionC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
