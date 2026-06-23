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

### Turtle API

`forward()` `back()` `turnLeft()` `turnRight()` `dig()` `place()` (from the
selected slot) `place(id)` (e.g. `"minecraft:stone"`) `detect()` `inspect()`
`getFuelLevel()` `refuel(n)` `select(n)` `getSelectedSlot()` `getItemCount(n)`

Global: `print(...)`. There is also a `/js <expression>` command for one-off evaluation.

### Wired networking (in development)

Each computer has one NIC on the face opposite its screen. Connect Cable to that
face to send frames to computers in the same connected cable segment. Addresses
remain stable while the world is saved.

```js
print(net.address());                 // this computer's MAC-like address
net.send("02:12:34:56:78:9a", "hello");

var frame = net.receive();            // null when no frame is queued
if (frame) print(frame.source, frame.data);
```

Payloads are currently strings up to 4 KiB and each NIC queues up to 64 frames.

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
for player-written switches. There is no background listener yet: programs read
and forward the frames queued at the time they run.

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
- ⏳ Peripherals (monitors, redstone I/O)
- 🌟 In-game code editor with type completion
- 🔭 NeoForge support

## Contributing

Issues and pull requests are welcome. Working language is English.

## License

MIT — see [LICENSE](./LICENSE).

This mod bundles [Mozilla Rhino](https://github.com/mozilla/rhino) (MPL-2.0); see
[THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md).

## Acknowledgements

Inspired by [CC:Tweaked](https://tweaked.cc/). Built with
[Mozilla Rhino](https://github.com/mozilla/rhino) and [Fabric](https://fabricmc.net/).
