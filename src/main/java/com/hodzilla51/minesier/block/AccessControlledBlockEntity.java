package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.net.AccessActionC2S;
import com.hodzilla51.minesier.net.AccessPromptS2C;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shared credential access policy for programmable and managed blocks. */
public interface AccessControlledBlockEntity {
  String MODE_UNCONFIGURED = "unconfigured";
  String MODE_PUBLIC = "public";
  String MODE_PASSWORD = "password";

  String accessMode();

  String passwordSalt();

  String passwordHash();

  boolean isAuthorized(UUID player);

  void authorize(UUID player);

  void clearAuthorizations();

  void setAccessState(String mode, String salt, String hash);

  default boolean canAccess(ServerPlayer player) {
    return MODE_PUBLIC.equals(accessMode())
        || (MODE_PASSWORD.equals(accessMode()) && isAuthorized(player.getUUID()));
  }

  default boolean ensureAccess(ServerPlayer player) {
    if (canAccess(player)) {
      return true;
    }
    sendAccessPrompt(player);
    return false;
  }

  default void sendAccessPrompt(ServerPlayer player) {
    if (this instanceof BlockEntity be) {
      ServerPlayNetworking.send(player, new AccessPromptS2C(be.getBlockPos(), accessMode()));
    }
  }

  default void handleAccessAction(ServerPlayer player, int action, String secret) {
    String mode = accessMode();
    if (action == AccessActionC2S.MAKE_PUBLIC) {
      if (MODE_UNCONFIGURED.equals(mode)
          || passwordHash().isBlank()
          || isAuthorized(player.getUUID())) {
        setAccessState(MODE_PUBLIC, "", "");
        clearAuthorizations();
        message(player, "MineSIer: access is now public");
      } else {
        sendAccessPrompt(player);
      }
      return;
    }
    if (action == AccessActionC2S.SET_PASSWORD) {
      if (secret.isBlank()) {
        message(player, "MineSIer: password cannot be blank");
        return;
      }
      if (MODE_UNCONFIGURED.equals(mode)
          || passwordHash().isBlank()
          || isAuthorized(player.getUUID())) {
        String salt = CredentialHash.newSalt();
        setAccessState(MODE_PASSWORD, salt, CredentialHash.hash(salt, secret));
        clearAuthorizations();
        authorize(player.getUUID());
        message(player, "MineSIer: password set");
      } else {
        sendAccessPrompt(player);
      }
      return;
    }
    if (action == AccessActionC2S.UNLOCK) {
      if (MODE_PASSWORD.equals(mode)
          && CredentialHash.matches(passwordSalt(), passwordHash(), secret)) {
        authorize(player.getUUID());
        message(player, "MineSIer: unlocked; interact again");
      } else {
        message(player, "MineSIer: access denied");
        sendAccessPrompt(player);
      }
    }
  }

  private static void message(ServerPlayer player, String message) {
    player.sendOverlayMessage(Component.literal(message));
  }

  final class CredentialHash {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialHash() {}

    static String newSalt() {
      byte[] bytes = new byte[16];
      RANDOM.nextBytes(bytes);
      return Base64.getEncoder().encodeToString(bytes);
    }

    static String hash(String salt, String secret) {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(secret.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest.digest());
      } catch (java.security.NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 unavailable", e);
      }
    }

    static boolean matches(String salt, String expected, String secret) {
      return !salt.isBlank()
          && !expected.isBlank()
          && MessageDigest.isEqual(
              expected.getBytes(StandardCharsets.UTF_8),
              hash(salt, secret).getBytes(StandardCharsets.UTF_8));
    }
  }
}
