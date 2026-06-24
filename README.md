# MineSIer

[English](./README.md) · [日本語](./README.ja.md)

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-green.svg)
![Loader: Fabric](https://img.shields.io/badge/Loader-Fabric-blueviolet.svg)

**Write JavaScript inside Minecraft.** Programmable computers and turtles you can
code from within the world — in the spirit of [CC:Tweaked](https://tweaked.cc/),
but in JavaScript instead of Lua.

> ⚠️ **Experimental / early development.** Targets Minecraft 26.2 (Fabric). Things
> may change. Feedback and contributions welcome.

## Features

- **Computer block** — place it, right-click to open a terminal with a multi-line
  editor. Write JavaScript, hit **Run** (or `Ctrl`/`Cmd`+`Enter`), see the output.
- **`print(...)`** and a scrollback transcript, so programs emit more than a value.
- **Sandboxed VM per block** — each computer runs its own isolated JavaScript engine
  (no host file/network/reflection access; runaway loops are cut off).
- **Programmable turtle** — a robot you move and command with code:
  `forward` / `back` / `turnLeft` / `turnRight`, `dig` / `place`, `detect` /
  `inspect`, fuel. It moves **one block at a time with smooth animation**, and
  reacts to the world as it runs.
- **Inventory** — turtles collect what they dig and place from a selected slot.
- **Disks** — save programs onto a disk item. The data lives on the disk, so you can
  pop it out and carry your programs to another computer.
- **Persistent** — computer/turtle state is saved with the world.

## Quick start

Get the blocks (creative or commands):

```
/give @s minesier:computer
/give @s minesier:turtle
/give @s minesier:disk
```

**Computer** — place it, right-click, type and Run:

```js
1 + 1                       // => 2
for (var i = 0; i < 3; i++) print("hello " + i);
```

**Turtle** — place it (it faces the way you're looking), right-click, and run a
program. This digs forward 5 blocks, mining anything in the way:

```js
for (var i = 0; i < 5; i++) {
  if (turtle.detect()) turtle.dig();
  turtle.forward();
}
print("done, fuel = " + turtle.getFuelLevel());
```

**Disks** — hold a disk and right-click a computer/turtle to insert it. Write a
program, type a name, and press **Save**. Press **Eject**, carry the disk to
another computer, insert it, and **Load** — your program comes with it.

Program names can use `/` as a folder separator (`lib/crypto`, `net/router`).
**List** shows the saved programs as an indented folder tree.

### Turtle API

`forward()` `back()` `turnLeft()` `turnRight()` `dig()` `place()` (from the
selected slot, consuming one block item) `place(id)` (requires the selected
block to match, e.g. `"minecraft:stone"`) `detect()` `inspect()`
`getFuelLevel()` `refuel(n)` `wait(ticks)` `select(n)` `getSelectedSlot()`
`getItemCount(n)`

Turtles also expose the same `net` and `net.nic(side)` API as Computers. Their
MAC-like addresses and queued frames survive block-by-block movement.

Global: `print(...)`. There is also a `/js <expression>` command for one-off evaluation.

### Redstone I/O

Any computer can read and drive redstone on its six faces. Sides are named
relative to the screen — the same names as the network interfaces: `front`,
`back`, `left`, `right`, `up`, `down`. Levels use the vanilla analog range
`0..15`; `setOutput` also accepts a boolean (`false`/`true` → `0`/`15`).

```js
redstone.getInput("back");        // 0..15 entering the back face (0 is falsy)
if (redstone.getInput("back")) print("powered!");

redstone.setOutput("front", true); // emit full signal out the front
redstone.setOutput("up", 7);       // emit analog strength 7 upward
redstone.getOutput("up");          // 7
redstone.getSides();               // ["front","back","left","right","up","down"]
```

Outputs persist with the world and keep emitting after the program ends, so a
computer can latch a lamp, door, or piston on until told otherwise.

### Monitors

Place a Monitor block next to a computer and write to it from code. The monitor
is addressed by side, like the redstone and network interfaces.

```js
var screen = monitor.at("right");  // null if there's no monitor on that face
screen.write("hello\nworld");      // append lines (oldest scroll off the top)
screen.setLine(1, "header");       // set one row (1-based)
screen.setText("a\nb\nc");         // replace everything
screen.clear();
screen.rows();                     // 17
screen.columns();                  // 26
```

The text is drawn on the monitor's screen face in the world, so you can read it
without opening any GUI.

### Resident execution (daemons)

A program can keep running after you close the terminal by registering timers.
This makes a computer a little always-on server: poll inputs, drive outputs,
answer the network.

```js
every(20, function () {            // run every 20 ticks (1 second)
  var on = redstone.getInput("back");
  monitor.at("front").setLine(1, on ? "RUNNING" : "idle");
});

after(100, function () {          // run once, 5 seconds from now
  print("warmed up");
});
```

`every(ticks, fn)` repeats; `after(ticks, fn)` fires once. Each callback runs on
the server tick within a 100,000-instruction safety budget, so a daemon can't
freeze the server. Re-running a program (or calling `clearTimers()`) stops the
old timers; removing the computer stops everything. Output printed by a callback
is appended to the transcript and shown next time you open the terminal.

A running daemon **survives a world reload**: the program that started it is
saved with the computer and re-run automatically when its chunk loads (JS state
itself isn't serialized, so top-level setup runs again — keep it idempotent).
Timers only fire while the chunk is loaded.

You can also boot a daemon from a disk: save a program named `startup`, and it
runs automatically the moment that disk is inserted into a computer.

### Wired networking (in development)

See the [networking specification](docs/networking.md) for the canonical API,
packet formats, limits, and implementation status.

Computers and Turtles have one NIC on every face. Connect Cable to the desired
face to exchange frames with devices in the same connected cable segment.
Addresses remain stable while the world is saved, including when a Turtle moves.

```js
print(net.address());                 // this computer's MAC-like address
net.send("02:12:34:56:78:9a", "hello");

var frame = net.receive();            // null when no frame is queued
if (frame) print(frame.source, frame.data);
```

Payloads are currently strings up to 4 KiB and each NIC queues up to 64 frames.

### Broadcast and address resolution (ARP)

`net.broadcast()` returns the L2 broadcast address — every NIC on the segment
accepts a frame sent to it. That's the primitive you need to write address
resolution yourself: ask "who has this address?" to everyone, and let the owner
answer. ARP is intentionally not built in — you build it, which is the point.

```js
// Responder: answer "who-has" broadcasts for my logical name.
net.nic("back").onReceive(function (frame) {
  if (frame.destination === net.broadcast() && frame.data === "who-has:node-a") {
    net.send(frame.source, "is-at:node-a");   // unicast reply with my MAC
  }
});

// Requester: find node-a's MAC, then talk to it directly.
net.send(net.broadcast(), "who-has:node-a");
var reply = net.receive();                     // "is-at:node-a" from node-a's MAC
if (reply) net.send(reply.source, "hello node-a");
```

### Multiple NICs and promiscuous receive

Every computer face is a separate NIC. `front` is the screen face; `back` is the
opposite face, and is the interface used by the original `net.*` shortcuts.
`left`, `right`, `up`, and `down` are relative to the computer. `forward` is an
alias for `front`.

```js
var front = net.nic("front");
var back = net.nic("back");

print(front.address());
front.setPromiscuous(true);

var frame = front.receive();
if (frame) back.forward(frame); // preserve source and destination: a simple bridge
```

`send(destination, data)` originates a new frame with that NIC's address as its
source. `forward(frame)` sends an existing frame unchanged, which is the basis
for player-written switches. `onReceive(handler)` registers a tick-budgeted
event handler: it runs only when a matching frame arrives, not in a busy loop.
Each server tick dispatches at most four receive events globally, and a handler
has a 100,000-instruction safety budget.

The repository includes [a two-port bridge](examples/two_port_bridge.js) and
[a static router](examples/static_router.js). They are deliberately ordinary JS
programs rather than special blocks: the same multi-NIC computer can be a host,
a bridge, a switch, or a router depending on the code loaded into it.

### Managed switch

The Switch block is a six-port learning switch for quick, beginner-friendly L2
networks. It learns source addresses per ingress port, unicasts known destinations,
and floods unknown destinations to every other connected port. It has no spanning
tree protocol: physical loops are bounded by the network event queue, but should be
avoided unless you are deliberately experimenting with loop behaviour.

### IPv4-style packets

`ip` provides a lossless, IPv4-inspired layer-3 packet envelope. Packets carry
IPv4 dotted-quad source and destination addresses, a TTL, an IP protocol number
(for example TCP is `6` and UDP is `17`), and a string payload. The envelope is
carried inside a normal `net` frame. It deliberately omits checksums, fragmentation,
options, ARP, and DHCP for now.

```js
var packet = ip.create("10.0.1.10", "10.0.2.20", 17, "hello");
net.send("gateway-mac-address", ip.encode(packet));
```

`ip.forward(packet)` decrements TTL and returns `null` when it reaches zero.

### Cryptography

`crypto` exposes standard primitives for player-built VPNs and authenticated
protocols. Binary values are Base64 strings. Use X25519 to establish a shared
secret, HKDF-SHA-256 to derive an AES key, and AES-GCM to encrypt and authenticate
payloads.

```js
var keys = crypto.x25519KeyPair();
// Exchange keys.publicKey with the peer, then:
var shared = crypto.x25519SharedSecret(keys.privateKey, peerPublicKey);
var key = crypto.hkdfSha256(shared, "", "minesier-vpn-v1", 32);
var encrypted = crypto.aesGcmEncrypt(key, "hello");
```

Also available: `randomBytes(count)`, `sha256(data)`, and `hmacSha256(key, data)`.
See [the secure tunnel example](examples/secure_tunnel.js) for a two-endpoint
encrypted channel built on top of `net`.

## Building from source

Requirements: **JDK 25**, Fabric (Loader + API for MC 26.2).

```
./gradlew build        # produces build/libs/minesier-*.jar
./gradlew runClient    # launch a dev client
```

## How it works

- JavaScript runs on **Mozilla Rhino** in interpreted mode, sandboxed with a
  safe scope + a deny-all class shutter + an instruction-count limit.
- Execution is **server-authoritative** — the client only shows the terminal and
  sends commands.
- Turtle programs run **tick-paced** on a worker thread that hands each action to
  the server tick, so actions take real time (and the program can react to results).
  Movement is a CC-style block "hop" drawn with a custom renderer for the slide.
- Saved programs are stored as a **data component** on the disk item, so the data
  travels with the medium rather than being tied to a position.

## Roadmap

- ✅ JS engine, computer + terminal/editor, programmable turtle, disks
- ⏳ Wired computer networking (player-built switches, routers, and VPNs)
- ✅ Redstone I/O (read/drive signals on any face from a program)
- ✅ Monitors (in-world text display driven from code)
- ✅ Resident execution (timers that survive terminal close and world reload, plus startup disks)
- 🌟 In-game code editor with type completion
- 🔭 NeoForge support

## Contributing

Issues and pull requests are welcome. Working language is English. See
[CONTRIBUTING.md](CONTRIBUTING.md) for development, formatting, and pull request
guidelines. Security-sensitive reports belong in [SECURITY.md](SECURITY.md), not
in public issues. Community expectations are in [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

MIT — see [LICENSE](./LICENSE).

This mod bundles [Mozilla Rhino](https://github.com/mozilla/rhino) (MPL-2.0); see
[THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md).

## Acknowledgements

Inspired by [CC:Tweaked](https://tweaked.cc/). Built with
[Mozilla Rhino](https://github.com/mozilla/rhino) and [Fabric](https://fabricmc.net/).
