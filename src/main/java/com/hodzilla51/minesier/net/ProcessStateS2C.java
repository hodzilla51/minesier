package com.hodzilla51.minesier.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: the resident-process state of the open computer. Sent when a program starts or
 * stops, and when the terminal screen is first opened.
 */
public record ProcessStateS2C(boolean running, String processName) implements CustomPacketPayload {
  public static final Type<ProcessStateS2C> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "process_state"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ProcessStateS2C> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.BOOL,
          ProcessStateS2C::running,
          ByteBufCodecs.STRING_UTF8,
          ProcessStateS2C::processName,
          ProcessStateS2C::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
