package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client terminal state for the computer at {@code pos}.
 *
 * @param open {@code true} = open/replace the terminal screen; {@code false} = update the
 *     transcript of an already-open screen.
 */
public record TerminalScreenS2C(BlockPos pos, String transcript, boolean open, boolean turtle)
    implements CustomPacketPayload {
  public static final Type<TerminalScreenS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "terminal_screen"));

  /** Convenience for non-turtle / update packets (turtle flag only matters when opening). */
  public TerminalScreenS2C(BlockPos pos, String transcript, boolean open) {
    this(pos, transcript, open, false);
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, TerminalScreenS2C> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          TerminalScreenS2C::pos,
          ByteBufCodecs.STRING_UTF8,
          TerminalScreenS2C::transcript,
          ByteBufCodecs.BOOL,
          TerminalScreenS2C::open,
          ByteBufCodecs.BOOL,
          TerminalScreenS2C::turtle,
          TerminalScreenS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
