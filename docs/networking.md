# Networking Specification

This is the canonical specification for MineSIer networking. The mod provides
the physical medium and Layer 2 primitives; players build higher-layer
protocols and programmable network devices in JavaScript.

## Scope and layering

| Layer | Mod responsibility | Player responsibility |
| --- | --- | --- |
| Physical / Layer 1 | Cable topology, wireless media, port attachment | Network layout |
| Layer 2 | NIC addresses, frames, broadcast, switching primitives | Bridges, switches, filtering |
| Layer 3+ | IPv4-style packets and crypto helpers | Routing, ARP, NAT, VPNs, TCP/UDP-like protocols |

The managed Switch block is an optional beginner-friendly Layer 2 device. A
multi-NIC computer running JavaScript remains the programmable path for all
network devices.

## Cable segments

A connected component of Cable blocks is one shared Layer 2 segment. A
transmission is offered to every NIC attached to that segment. Cable has no
latency, collision, loss, MTU, or bandwidth model yet.

The current Cable model is intentionally simple. Future shared-medium queues
and bandwidth limits are tracked in GitHub issue #12.

## NICs

Every Computer and Turtle face is an independent NIC. JavaScript names faces
relative to the device's front:

| Name | Physical face |
| --- | --- |
| `front` or `forward` | Screen face |
| `back` | Opposite the screen |
| `left`, `right` | Relative horizontal faces |
| `up`, `down` | Vertical faces |

`net.address()`, `net.send()`, and `net.receive()` use the `back` NIC for
compatibility. Use `net.nic(name)` for a specific NIC.

Each NIC has a stable MAC-like address while its device remains saved in the
world. A Turtle carries its addresses across block-by-block movement.
`net.nic(name).address()` returns its address.

## Layer 2 frames

Frames exposed to JavaScript have this shape:

```js
{
  source: "02:aa:bb:cc:dd:ee",
  destination: "02:11:22:33:44:55",
  data: "string payload"
}
```

Payloads are UTF-8 strings limited to 4 KiB. A NIC normally accepts only frames
addressed to itself. `nic.setPromiscuous(true)` accepts all frames reaching that
physical port.

`net.broadcast()` returns `ff:ff:ff:ff:ff:ff`. Every NIC on a segment accepts
frames sent to this destination. It is the primitive for player-written ARP and
other discovery protocols.

Frames retain a forwarding-hop counter. A frame is dropped after 16 forwards to
bound accidental Layer 2 loops. This is a simulation safeguard, not Ethernet
TTL or a replacement for spanning tree.

### JavaScript API

```js
var nic = net.nic("front");

nic.address();
nic.send(destinationMac, data);
nic.receive();                    // frame or null
nic.forward(frame);               // preserves source and destination
nic.setPromiscuous(true);
nic.onReceive(function (frame) { /* event-driven handler */ });
nic.offReceive();
```

`send()` originates a frame with the selected NIC address as its source.
`forward()` emits the supplied frame unchanged except for its loop-protection
hop count.

## Event-driven handlers

`onReceive(handler)` registers a persistent receive handler. It does not spin
in a busy loop. Handlers run only when matching frames arrive.

The current runtime has a global queue of 1,024 pending network actions. At
most four actions are dispatched per server tick, and each JavaScript callback
has a 100,000-instruction budget. Re-running a Computer program or removing the
Computer clears its handlers.

## Managed Switch block

The Switch is a six-port learning switch. It learns source MAC addresses by
ingress port, forwards known unicast destinations only to the learned egress
port, and floods unknown destinations and broadcast frames to all other ports.

Its MAC table is bounded to 256 entries and ages entries after five minutes of
Minecraft server time. It currently has no STP, VLAN, QoS, or management UI.

## IPv4-style packet envelope

The `ip` global provides an IPv4-inspired Layer 3 packet carried inside a Layer
2 frame payload. It uses IPv4 dotted-quad addresses, a TTL, a protocol number,
and a string payload. It is not an RFC 791 byte encoding.

```js
var packet = ip.create("10.0.1.10", "10.0.2.20", 17, "hello");
net.send(gatewayMac, ip.encode(packet));

var received = ip.decode(frame.data);
var routed = ip.forward(received); // decrements TTL; null when it expires
```

Protocol numbers follow IPv4 convention where useful: TCP is `6` and UDP is
`17`. The internal wire envelope begins with `MSIP4|`; this is a MineSIer format,
not raw IPv4 bytes.

IPv4 checksums, fragmentation, options, ARP cache management, and DHCP are not
implemented. Address resolution is intentionally player-written using Layer 2
broadcast.

## Cryptography

The `crypto` global exposes standard primitives for player-built authenticated
protocols and VPNs. Binary values are Base64 strings.

```js
var local = crypto.x25519KeyPair();
var shared = crypto.x25519SharedSecret(local.privateKey, peerPublicKey);
var aesKey = crypto.hkdfSha256(shared, "", "minesier-vpn-v1", 32);
var encrypted = crypto.aesGcmEncrypt(aesKey, "hello");
var plaintext = crypto.aesGcmDecrypt(aesKey, encrypted.nonce, encrypted.ciphertext);
```

Available functions:

- `randomBytes(count)`
- `sha256(data)`
- `hmacSha256(base64Key, data)`
- `x25519KeyPair()`
- `x25519SharedSecret(base64PrivateKey, base64PublicKey)`
- `hkdfSha256(base64Input, base64Salt, info, length)`
- `aesGcmEncrypt(base64Key, plaintext[, additionalData])`
- `aesGcmDecrypt(base64Key, base64Nonce, base64Ciphertext[, additionalData])`

AES-GCM decryption returns `null` when authentication fails.

## Examples

- [`two_port_bridge.js`](../examples/two_port_bridge.js)
- [`static_router.js`](../examples/static_router.js)
- [`secure_tunnel.js`](../examples/secure_tunnel.js)
