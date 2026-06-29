# MineSIer JavaScript Reference

This is the full reference for the JavaScript you write inside a MineSIer
**Computer** or **Turtle**. It is organized in three layers so you can jump in
at the level you need:

1. **Plain JavaScript** — the language itself (variables, functions, loops…).
   We keep this thin and link out to MDN; it is the same JavaScript you'd write
   anywhere. See [javascript-primer.md](javascript-primer.md).
2. **The MineSIer runtime** — how *this* environment differs from a browser or
   Node.js: the sandbox, the instruction budget, how programs keep running, and
   how `require` loads other files. See [runtime.md](runtime.md). **Read this
   one even if you already know JS** — the differences will bite you otherwise.
3. **The device API** — the globals that actually touch the world: `turtle`,
   `net`, `redstone`, `monitor`, `fs`, `crypto`, `ip`. One file each, below.

## Quick start

Open a Computer or Turtle, type a line, press Enter:

```js
print("hello, world")
```

`print(...)` is your output — there is no `console.log` here (see
[runtime.md](runtime.md#output-print)).

A turtle program is just JavaScript that calls the `turtle` global:

```js
for (let i = 0; i < 4; i++) {
  turtle.forward()
  turtle.turnRight()
}
```

## Device API reference

| Global | What it does | Available when | Doc |
|--------|--------------|----------------|-----|
| `print` | Write a line of output | always | [runtime.md](runtime.md#output-print) |
| `minesier` | API version info | always | [runtime.md](runtime.md#version-info-minesier) |
| `require` | Load another file as a module | always | [runtime.md](runtime.md#modules-require) |
| `every` / `after` / `clearTimers` | Background timers (resident execution) | always | [runtime.md](runtime.md#background-timers-every--after) |
| `fs` | Read/write files, mount drives | always | [api-filesystem.md](api-filesystem.md) |
| `turtle` | Move, dig, place, inspect | on a **Turtle** | [api-turtle.md](api-turtle.md) |
| `net` | Send/receive network frames | when a **cable** is attached | [api-network.md](api-network.md) |
| `ip` | Build/parse IPv4-style packets | always | [api-network.md](api-network.md#ip-layer-3-helpers) |
| `redstone` | Read/drive analog redstone | always (on a Computer) | [api-redstone.md](api-redstone.md) |
| `monitor` | Draw text on a Monitor block | when a **Monitor** is adjacent | [api-monitor.md](api-monitor.md) |
| `crypto` | Hashes, HMAC, AES-GCM, X25519 | always | [api-crypto.md](api-crypto.md) |

## Conventions used in these docs

- **Signature** lines show argument names and `?` for optional arguments, e.g.
  `turtle.place(blockId?)`.
- "Returns `false` on failure" means the call does **not** throw — it returns a
  falsy value you should check. A few calls *do* throw (noted explicitly).
- Side names everywhere (`net`, `redstone`, `monitor`) are **relative to the
  device's screen**: `front`, `back`, `left`, `right`, `up`, `down`.
