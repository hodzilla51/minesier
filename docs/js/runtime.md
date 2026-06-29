# The MineSIer Runtime

This page covers everything about *how* your JavaScript runs that is specific to
MineSIer. Even experienced JS programmers should read it — the sandbox, the
instruction budget, and the resident-execution model all behave differently from
a browser or Node.js.

## The engine

Programs run on **Mozilla Rhino 1.8**, embedded inside the Minecraft server and
executed in interpreted mode. The language is modern (ES2015+ syntax works, see
[the primer](javascript-primer.md#whats-available)), but the *host environment*
is intentionally minimal and locked down.

### What is NOT available

| You might expect | Use instead |
|------------------|-------------|
| `console.log(...)` | [`print(...)`](#output-print) |
| `setTimeout` / `setInterval` | [`every` / `after`](#background-timers-every--after) |
| `fetch` / `XMLHttpRequest` | the [`net`](api-network.md) global (in-world cables) |
| `import x from "..."` (ES modules) | [`require(...)`](#modules-require) (CommonJS) |
| `java.*`, `Packages`, reflection | **blocked** — see the sandbox below |
| browser/DOM globals (`window`, …) | none — this isn't a browser |

## The sandbox

The VM is created with Rhino's *safe* standard objects and a class shutter that
**denies all access to Java classes**. There is no way to reach Minecraft
internals, the filesystem outside your drives, the network outside the in-world
cables, or any Java API except the globals documented here. This is what makes
it safe to run other players' programs.

Practical consequences:

- You cannot load arbitrary Java libraries. Everything you can do is in these
  docs.
- File access goes through [`fs`](api-filesystem.md) (your drives only).
- Network access goes through [`net`](api-network.md) (in-world cables only).

## The instruction budget

To stop a buggy or hostile program from freezing the server, every program runs
under a hard **instruction budget**. When it's exhausted, the program is aborted
with an error — it does not hang the game.

| Context | Budget (default) |
|---------|------------------|
| A top-level program run | **200,000,000** instructions |
| A timer / receive callback | **100,000** instructions |

The smaller callback budget matters: code inside `every`/`after`/`onReceive`
must be *short*. Do a little work each tick, not a lot once. (These limits are
configurable in `config/minesier.properties`.)

> This is why you should never write `while (true) { ... }` to keep a program
> alive — it will burn the budget and get killed. Use a timer instead.

## Output: `print`

```js
print(value, ...)
```

Appends one line to the terminal, space-joining its arguments (like
`console.log`). Everything is converted to a string. The on-screen scrollback
keeps the most recent **200** lines.

```js
print("fuel:", turtle.getFuelLevel())
print({ x: 1, y: 2 })   // objects stringify to [object Object]; build the
                         // string yourself or use JSON.stringify for detail
```

If a program's *last expression* has a value, that value is also printed (handy
for one-liners at the prompt):

```js
2 + 2        // prints 4
```

## Version info: `minesier`

```js
minesier.apiVersion        // a number, currently 1
minesier.apiVersionString  // the same as a string, "1"
```

Use this to guard programs that rely on newer APIs.

## Modules: `require`

```js
const lib = require(name)
```

Loads **another file on your drives** and returns its `module.exports`, just
like CommonJS in Node.js. This is *not* npm — `name` is a path into your own
filesystem.

```js
// C:/mathutil.js
function add(a, b) { return a + b }
module.exports = { add }
```

```js
// your program
const m = require("mathutil")        // ".js" is added automatically
const m2 = require("mathutil.js")    // also works
const m3 = require("C:/lib/util.js") // explicit drive + path
print(m.add(2, 3))                   // 5
```

Details:

- The **`.js` extension is optional** — `require("foo")` tries `foo`, then
  `foo.js`.
- Paths follow the same drive rules as [`fs`](api-filesystem.md): no prefix
  means `C:`. You can require from any mounted drive, including
  [player-mounted ones](api-filesystem.md#mounting-your-own-drive).
- Each module runs in its own scope (its top-level `var`/`let` stay private) but
  shares the same globals.
- Results are **cached per run**, and circular `require`s resolve to the
  partially-built exports instead of looping forever.
- `require` throws if the module isn't found.

## Background timers (`every` / `after`)

This is **resident execution**: a program can keep running after you close the
terminal, driven by the server tick (20 ticks = 1 second).

```js
every(ticks, fn)   // run fn repeatedly, every `ticks` ticks. returns true
after(ticks, fn)   // run fn once, after `ticks` ticks. returns true
clearTimers()      // cancel ALL timers on this device (the "stop" button)
```

- `ticks` is clamped to a minimum of 1.
- `fn` runs on the server thread under the **100,000-instruction callback
  budget** — keep it short.
- A device with any live timer keeps ticking even after its terminal is closed.
- `clearTimers()` stops everything; it's the script-level way to make a resident
  program quit.

```js
let n = 0
every(20, () => {          // once per second
  n++
  print("uptime: " + n + "s")
  if (n >= 10) clearTimers()
})
```

> **Persistence caveat:** as of now, timers survive the terminal closing but
> **not** a world reload — a reloaded world starts with no resident programs.
> Auto-restart (a `startup` program) is planned but not yet implemented.

## Threading & timing notes

- Everything runs on the **server thread**, so your code sees a consistent world
  and never races with the game. The flip side is the budget: don't block.
- Turtle actions (`turtle.forward()`, `dig`, …) are **tick-paced** — they take
  real game time and your program is suspended between them. See
  [api-turtle.md](api-turtle.md).
