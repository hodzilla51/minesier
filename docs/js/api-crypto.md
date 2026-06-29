# `crypto` — Cryptography

The `crypto` global provides standard cryptographic primitives so you can build
secure protocols on top of the (inherently sniffable) [`net`](api-network.md)
layer — the defensive half of MineSIer's security/CTF pillar. It's available
everywhere.

## Data format convention

**Binary in, binary out is Base64; text is UTF-8.** Every key, nonce, digest,
ciphertext, and random blob is a **Base64 string**. Plaintext and `info`/`aad`
context strings are plain UTF-8 text. Mixing these up is the most common
mistake — e.g. an AES key must be a Base64 string that decodes to 16/24/32
bytes, not a 16-character password.

## Random bytes

```js
crypto.randomBytes(count)   // -> Base64 string of `count` random bytes
```

`count` must be **1–4096**. Use it for keys, nonces, salts, challenges.

```js
const key = crypto.randomBytes(32)   // a 256-bit AES key (Base64)
```

## Hashing & MACs

```js
crypto.sha256(data)               // -> Base64 SHA-256 of the UTF-8 text `data`
crypto.hmacSha256(key, data)      // -> Base64 HMAC-SHA-256; key is Base64,
                                  //    data is UTF-8 text
crypto.hkdfSha256(input, salt, info, length)
                                  // -> Base64, `length` bytes derived from
                                  //    `input` (Base64). salt is Base64 (may be
                                  //    ""), info is UTF-8 context text.
```

```js
const digest = crypto.sha256("hello")
const tag = crypto.hmacSha256(key, "message to authenticate")

// Derive a 32-byte session key from a shared secret
const sessionKey = crypto.hkdfSha256(sharedSecret, "", "session-v1", 32)
```

## Authenticated encryption: AES-GCM

```js
crypto.aesGcmEncrypt(key, plaintext, aad?)
// -> { nonce, ciphertext }   (both Base64)

crypto.aesGcmDecrypt(key, nonce, ciphertext, aad?)
// -> plaintext string, or null if authentication fails
```

- `key` is Base64 decoding to **16, 24, or 32 bytes**.
- A fresh random 12-byte nonce is generated per encryption and returned to you —
  send it alongside the ciphertext.
- `aad` (optional) is additional authenticated data: text that is *authenticated
  but not encrypted*. The same `aad` must be supplied to decrypt.
- **Decrypt returns `null`** (it does not throw) when the ciphertext or tag was
  tampered with — always check for `null`.

```js
const key = crypto.randomBytes(32)

const enc = crypto.aesGcmEncrypt(key, "secret message")
net.send(dest, JSON.stringify(enc))   // send { nonce, ciphertext }

// on the other side:
const { nonce, ciphertext } = JSON.parse(frame.data)
const msg = crypto.aesGcmDecrypt(key, nonce, ciphertext)
if (msg === null) {
  print("tampered or wrong key!")
} else {
  print("got: " + msg)
}
```

## Key agreement: X25519

```js
crypto.x25519KeyPair()                       // -> { privateKey, publicKey }
crypto.x25519SharedSecret(privateKey, publicKey)  // -> Base64 shared secret
```

Both keys are Base64. Each party generates a key pair, exchanges **public** keys
over the network, then derives the *same* shared secret from their own private
key and the other's public key. Run the secret through `hkdfSha256` to get an
AES key — don't use the raw shared secret directly.

```js
// Alice
const a = crypto.x25519KeyPair()
// ...send a.publicKey to Bob, receive bobPublic...
const shared = crypto.x25519SharedSecret(a.privateKey, bobPublic)
const key = crypto.hkdfSha256(shared, "", "handshake-v1", 32)
// now use `key` with aesGcmEncrypt/Decrypt
```

This gives you an end-to-end encrypted channel even though anyone with a
promiscuous NIC can read every frame on the cable. That asymmetry — the wire is
open, but the payload is sealed — is exactly the game MineSIer's security pillar
is built around.
