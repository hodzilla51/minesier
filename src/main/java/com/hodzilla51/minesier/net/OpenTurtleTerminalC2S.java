package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: reopen the terminal for a turtle after closing its storage menu. */
public record OpenTurtleTerminalC2S(BlockPos pos) implements CustomPacketPayload {
  public static final Type<OpenTurtleTerminalC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "open_turtle_terminal"));

  public static final StreamCodec<RegistryFriendlyByteBuf, OpenTurtleTerminalC2S> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC, OpenTurtleTerminalC2S::pos, OpenTurtleTerminalC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
