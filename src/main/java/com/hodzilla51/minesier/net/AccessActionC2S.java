package com.hodzilla51.minesier.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: submit a credential action for one device. */
public record AccessActionC2S(BlockPos pos, int action, String secret)
    implements CustomPacketPayload {
  public static final int UNLOCK = 0;
  public static final int SET_PASSWORD = 1;
  public static final int MAKE_PUBLIC = 2;

  public static final Type<AccessActionC2S> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath("minesier", "access_action"));

  public static final StreamCodec<RegistryFriendlyByteBuf, AccessActionC2S> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          AccessActionC2S::pos,
          ByteBufCodecs.INT,
          AccessActionC2S::action,
          ByteBufCodecs.STRING_UTF8,
          AccessActionC2S::secret,
          AccessActionC2S::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
