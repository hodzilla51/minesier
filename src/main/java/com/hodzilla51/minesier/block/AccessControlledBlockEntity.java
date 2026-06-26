package com.hodzilla51.minesier.block;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared owner/public-access policy for programmable and managed blocks. */
public interface AccessControlledBlockEntity {
  UUID ownerUuid();

  String ownerName();

  boolean publicAccess();

  void setOwner(ServerPlayer player);

  void setPublicAccess(boolean publicAccess);

  default boolean hasOwner() {
    return ownerUuid() != null;
  }

  default boolean canAccess(ServerPlayer player) {
    return !hasOwner() || publicAccess() || player.getUUID().equals(ownerUuid());
  }

  default boolean ensureAccess(ServerPlayer player) {
    if (!hasOwner()) {
      setOwner(player);
      message(player, "MineSIer: claimed for " + playerName(player));
      return true;
    }
    if (canAccess(player)) {
      return true;
    }
    message(player, "MineSIer: owned by " + ownerName());
    return false;
  }

  default boolean togglePublicAccess(ServerPlayer player) {
    if (!hasOwner()) {
      setOwner(player);
    }
    if (!player.getUUID().equals(ownerUuid())) {
      message(player, "MineSIer: owned by " + ownerName());
      return false;
    }
    boolean next = !publicAccess();
    setPublicAccess(next);
    message(player, "MineSIer: access is now " + (next ? "public" : "private"));
    return true;
  }

  static String playerName(ServerPlayer player) {
    return player.getPlainTextName();
  }

  private static void message(ServerPlayer player, String message) {
    player.sendOverlayMessage(Component.literal(message));
  }
}
