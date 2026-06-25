package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoApiTest {
  @Test
  void sha256IsStable() {
    assertEquals("LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=", CryptoApi.sha256("hello"));
  }

  @Test
  void aesGcmRoundTripAndAuthenticationFailure() {
    String key = Base64.getEncoder().encodeToString(new byte[16]);
    CryptoApi.Encrypted encrypted = CryptoApi.aesGcmEncrypt(key, "secret", "aad");

    assertEquals(
        "secret", CryptoApi.aesGcmDecrypt(key, encrypted.nonce(), encrypted.ciphertext(), "aad"));
    assertNull(
        CryptoApi.aesGcmDecrypt(key, encrypted.nonce(), encrypted.ciphertext(), "wrong aad"));
  }

  @Test
  void x25519SharedSecretMatchesBothDirections() {
    CryptoApi.X25519KeyPair a = CryptoApi.x25519KeyPair();
    CryptoApi.X25519KeyPair b = CryptoApi.x25519KeyPair();

    String ab = CryptoApi.x25519SharedSecret(a.privateKey(), b.publicKey());
    String ba = CryptoApi.x25519SharedSecret(b.privateKey(), a.publicKey());

    assertNotNull(ab);
    assertEquals(ab, ba);
    assertNotEquals("", ab);
  }

  @Test
  void rejectsInvalidRandomAndAesKeySizes() {
    assertThrows(IllegalArgumentException.class, () -> CryptoApi.randomBytes(0));
    assertThrows(IllegalArgumentException.class, () -> CryptoApi.aesGcmEncrypt("bad", "x", ""));
  }
}
