package com.hodzilla51.minesier.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server interaction with the open turtle storage menu.
 *
 * <p>{@code slot < 0} means "store the carried stack" (first-fit + merge across all turtle slots,
 * regardless of where in the box it was dropped). {@code slot >= 0} means "take from that turtle
 * slot": to the cursor when {@code shift} is false, or quick-moved into the player inventory when
 * {@code shift} is true. The server reads/writes the carried stack from the player's menu, so the
 * whole exchange is server-authoritative and dupe-safe.
 */
public record TurtleClickC2S(int slot, boolean shift) implements CustomPacketPayload {
  public static final Type<TurtleClickC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "turtle_click"));

  public static final StreamCodec<RegistryFriendlyByteBuf, TurtleClickC2S> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.VAR_INT,
          TurtleClickC2S::slot,
          ByteBufCodecs.BOOL,
          TurtleClickC2S::shift,
          TurtleClickC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
