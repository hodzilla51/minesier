# `redstone` — Redstone I/O

The `redstone` global lets a Computer read incoming redstone signals and drive
outgoing ones on any of its six faces. Signals are the vanilla **analog range
0–15**.

## Sides

Side names are relative to the computer's screen:

```
front, back, left, right, up, down
```

```js
redstone.getSides()  // -> ["front","back","left","right","up","down"]
```

## Reading input

```js
redstone.getInput(side)   // -> analog level 0..15 entering `side`,
                          //    or -1 if the side name is unknown
```

```js
if (redstone.getInput("back") > 0) {
  print("powered from behind")
}
```

## Driving output

```js
redstone.setOutput(side, level)  // level: 0..15, or a boolean (false=0, true=15)
                                 // -> true on success, false if side unknown
redstone.getOutput(side)         // -> the level we're currently emitting on
                                 //    `side`, or -1 if unknown
```

`level` is clamped to 0–15. A boolean is accepted as a shortcut: `true` → 15,
`false` → 0. Neighboring blocks are notified, so this behaves like a redstone
source.

Outputs **persist** (they're saved with the block), so a face you set stays
powered until you change it.

```js
redstone.setOutput("front", true)   // full power out the front
redstone.setOutput("up", 7)         // half-ish signal upward
redstone.setOutput("front", 0)      // off
```

## Example: a simple repeater/inverter

```js
// Mirror the back input to the front output, every tick.
every(1, () => {
  const level = redstone.getInput("back")
  redstone.setOutput("front", level)
})
```

```js
// Inverter: front is on only when back is off.
every(1, () => {
  redstone.setOutput("front", redstone.getInput("back") === 0 ? 15 : 0)
})
```

See [`every`](runtime.md#background-timers-every--after) for how these keep
running after you close the terminal, and the
[callback budget](runtime.md#the-instruction-budget) for why the timer body must
stay small.
