package com.hodzilla51.minesier.js;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Standard cryptographic primitives exposed as Base64 and UTF-8 safe script values. */
public final class CryptoApi {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int GCM_NONCE_BYTES = 12;
  private static final int MAX_RANDOM_BYTES = 4_096;
  private static final int MAX_HKDF_BYTES = 255 * 32;

  private CryptoApi() {}

  public static String randomBytes(int count) {
    if (count < 1 || count > MAX_RANDOM_BYTES)
      throw new IllegalArgumentException("random byte count must be 1-4096");
    byte[] bytes = new byte[count];
    RANDOM.nextBytes(bytes);
    return encode(bytes);
  }

  public static String sha256(String data) {
    try {
      return encode(MessageDigest.getInstance("SHA-256").digest(utf8(data)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public static String hmacSha256(String key, String data) {
    return encode(hmac(decode(key), utf8(data)));
  }

  public static String hkdfSha256(String input, String salt, String info, int length) {
    if (length < 1 || length > MAX_HKDF_BYTES)
      throw new IllegalArgumentException("HKDF length is out of range");
    byte[] prk = hmac(salt.isEmpty() ? new byte[32] : decode(salt), decode(input));
    byte[] context = utf8(info);
    byte[] output = new byte[length];
    byte[] previous = new byte[0];
    for (int counter = 1, offset = 0; offset < length; counter++) {
      byte[] block = Arrays.copyOf(previous, previous.length + context.length + 1);
      System.arraycopy(context, 0, block, previous.length, context.length);
      block[block.length - 1] = (byte) counter;
      previous = hmac(prk, block);
      int copied = Math.min(32, length - offset);
      System.arraycopy(previous, 0, output, offset, copied);
      offset += copied;
    }
    return encode(output);
  }

  public static Encrypted aesGcmEncrypt(String key, String plaintext, String aad) {
    try {
      byte[] nonce = new byte[GCM_NONCE_BYTES];
      RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, aesKey(key), new GCMParameterSpec(128, nonce));
      if (!aad.isEmpty()) cipher.updateAAD(utf8(aad));
      return new Encrypted(encode(nonce), encode(cipher.doFinal(utf8(plaintext))));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  /** Returns null when ciphertext authentication fails. */
  public static String aesGcmDecrypt(String key, String nonce, String ciphertext, String aad) {
    try {
      byte[] nonceBytes = decode(nonce);
      if (nonceBytes.length != GCM_NONCE_BYTES)
        throw new IllegalArgumentException("AES-GCM nonce must be 12 bytes");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, aesKey(key), new GCMParameterSpec(128, nonceBytes));
      if (!aad.isEmpty()) cipher.updateAAD(utf8(aad));
      return new String(cipher.doFinal(decode(ciphertext)), StandardCharsets.UTF_8);
    } catch (javax.crypto.AEADBadTagException badTag) {
      return null;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM decryption failed", e);
    }
  }

  public static X25519KeyPair x25519KeyPair() {
    try {
      KeyPair pair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
      return new X25519KeyPair(
          encode(pair.getPrivate().getEncoded()), encode(pair.getPublic().getEncoded()));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("X25519 unavailable", e);
    }
  }

  public static String x25519SharedSecret(String privateKey, String publicKey) {
    try {
      KeyFactory factory = KeyFactory.getInstance("X25519");
      KeyAgreement agreement = KeyAgreement.getInstance("X25519");
      agreement.init(factory.generatePrivate(new PKCS8EncodedKeySpec(decode(privateKey))));
      agreement.doPhase(factory.generatePublic(new X509EncodedKeySpec(decode(publicKey))), true);
      return encode(agreement.generateSecret());
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("invalid X25519 key", e);
    }
  }

  public record Encrypted(String nonce, String ciphertext) {}

  public record X25519KeyPair(String privateKey, String publicKey) {}

  private static SecretKeySpec aesKey(String encoded) {
    byte[] key = decode(encoded);
    if (key.length != 16 && key.length != 24 && key.length != 32)
      throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
    return new SecretKeySpec(key, "AES");
  }

  private static byte[] hmac(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA-256 unavailable", e);
    }
  }

  private static byte[] decode(String data) {
    try {
      return Base64.getDecoder().decode(data);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("expected Base64 data", e);
    }
  }

  private static String encode(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }

  private static byte[] utf8(String data) {
    return data.getBytes(StandardCharsets.UTF_8);
  }
}
