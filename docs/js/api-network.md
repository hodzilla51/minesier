# `net` & `ip` — Networking

MineSIer models a real network stack. The mod provides **layer 2** (physical
cables carrying frames between addresses); *you* build layer 3 and up in
JavaScript. The `net` global is the NIC; the `ip` global is a helper for writing
IPv4-style packets on top of it.

> `net` is only available when a **cable** is attached to the device. If there's
> no cable, the global is absent.

## Addressing

Every NIC has an address (assigned by the mod). Frames are delivered to a
destination address; there is also a broadcast address.

```js
net.address()     // -> this device's address (string)
net.broadcast     // -> the broadcast address (send here to reach everyone)
```

## Sending and receiving (single NIC)

```js
net.send(destination, data)  // -> true if injected onto the cable
net.receive()                // -> next frame, or null if the queue is empty
```

A received **frame** is an object:

| Field | Meaning |
|-------|---------|
| `source` | sender's address |
| `destination` | address it was sent to (possibly broadcast) |
| `data` | the string payload |
| `hops` | how many segments it has crossed |

```js
net.send(net.broadcast, "ping")

const frame = net.receive()
if (frame) {
  print(`${frame.source} -> ${frame.destination}: ${frame.data}`)
}
```

`receive()` is a **poll** — it returns `null` when nothing is queued. To react
to traffic continuously, either poll it from an
[`every`](runtime.md#background-timers-every--after) timer, or use a NIC
`onReceive` callback (below).

Payloads are capped (default **4 KB** per frame), and each NIC has a bounded
inbox (default **64** frames) — drain it promptly or frames are dropped.

## Multiple interfaces: `net.nic(side)`

A device can have cables on several faces. `net.nic(side)` returns a handle to
*one* physical interface, so you can route between them (this is how you write a
switch or router in JS). `side` is relative to the screen: `front`, `back`,
`left`, `right`, `up`, `down`.

```js
const eth0 = net.nic("front")  // -> nic handle, or null if no cable there
```

A NIC handle has these methods:

```js
eth0.address()                 // this interface's address, or null
eth0.send(destination, data)   // send out this interface
eth0.receive()                 // next frame on this interface, or null
eth0.forward(frame)            // re-emit a frame out this interface (routing)
eth0.setPromiscuous(enabled)   // true = receive frames NOT addressed to us
eth0.onReceive(fn)             // call fn(frame) for each arriving frame
eth0.offReceive()              // remove the onReceive callback
```

- **`setPromiscuous(true)`** lets the interface see frames not addressed to it —
  the basis for sniffing and the security/CTF side of the mod.
- **`onReceive(fn)`** is event-driven receiving: `fn` runs (under the
  100k-instruction [callback budget](runtime.md#the-instruction-budget)) for
  each frame, and keeps working after the terminal closes — making the device a
  resident network service.
- **`forward(frame)`** re-injects a frame, used when relaying between interfaces.

```js
// Minimal 2-port relay: forward everything from front out back, and vice versa.
const a = net.nic("front")
const b = net.nic("back")
a.setPromiscuous(true)
b.setPromiscuous(true)
a.onReceive((f) => b.forward(f))
b.onReceive((f) => a.forward(f))
```

## `ip` — Layer-3 helpers

`ip` builds and parses IPv4-inspired packets that you carry *inside* a frame's
`data` field. The mod doesn't route these for you — they're a structured
convention for your own protocols.

```js
ip.create(source, destination, protocol, payload, ttl?)  // -> packet object
ip.encode(packet)   // -> string, suitable for net.send(..., str)
ip.decode(string)   // -> packet object (parsed from a received frame's data)
ip.forward(packet)  // -> packet advanced one hop (ttl decremented)
```

`ttl` defaults to **64**. A packet object has:

| Field | Meaning |
|-------|---------|
| `version` | always `4` |
| `source` | source address |
| `destination` | destination address |
| `ttl` | hops remaining |
| `protocol` | a number identifying your protocol |
| `payload` | string payload |

```js
// Sender: wrap an IP packet inside a network frame
const pkt = ip.create(net.address(), "10.0.0.5", 6, "hello")
net.send("10.0.0.5", ip.encode(pkt))

// Receiver: unwrap it
const frame = net.receive()
if (frame) {
  const pkt = ip.decode(frame.data)
  print(`ip ${pkt.source} -> ${pkt.destination} proto=${pkt.protocol}: ${pkt.payload}`)
}

// Router: decrement TTL and pass it on
const next = ip.forward(pkt)
if (next.ttl > 0) eth0.forward(/* re-wrapped frame */)
```

For confidentiality and authentication on top of this, see
[`crypto`](api-crypto.md).
