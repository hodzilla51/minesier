# `turtle` — The Programmable Robot

The `turtle` global exists **only on a Turtle** (not a plain Computer). It moves
through the world, digs, places blocks, and inspects its surroundings.

## How turtle actions run

Turtle actions are **tick-paced**: each action takes real game time, and your
program is suspended in between (it doesn't busy-wait). A program can run for up
to **12,000 ticks (~10 minutes)** before the runtime stops it. Movement costs
fuel and **fails when fuel hits 0**.

Most actions **return a boolean** telling you whether they succeeded — always
check it instead of assuming:

```js
if (!turtle.forward()) {
  print("blocked or out of fuel")
}
```

## Movement

```js
turtle.forward()   // -> true if it moved
turtle.back()      // -> true if it moved
turtle.up()        // -> true if it moved
turtle.down()      // -> true if it moved
turtle.turnLeft()  // -> true
turtle.turnRight() // -> true
```

Each successful move consumes **1 fuel**. Movement returns `false` when blocked
or out of fuel.

```js
// Walk forward until something blocks the way
while (turtle.forward()) { /* keep going */ }
```

## Digging and placing

```js
turtle.dig()              // break the block ahead; false if nothing to dig
turtle.place(blockId)     // place ONLY if the selected slot matches blockId
turtle.place()            // place whatever is in the selected slot
```

- `dig()` breaks the block directly ahead and drops its items. Returns `false`
  if there's nothing breakable there.
- `place(blockId)` places the selected block ahead **only if** it matches
  `blockId` (e.g. `"minecraft:cobblestone"`); it consumes one item and returns
  `false` if the slot is empty, the wrong block, or the space is blocked.
- `place()` with no argument places whatever is in the selected slot.

```js
turtle.select(1)
if (turtle.place("minecraft:cobblestone")) {
  print("placed")
}
```

## Waiting

```js
turtle.wait(ticks?)   // pause this program for `ticks` ticks (default 1)
```

Pauses without blocking the server. Capped at **1,200 ticks (~60s)** per call.

## Sensing the world

```js
turtle.detect()    // -> true if a solid block is directly ahead
turtle.inspect()   // -> block id ahead, e.g. "minecraft:dirt", or "" if empty
turtle.scan()      // -> array of nearby blocks (needs a scan module equipped)
```

`scan()` returns an array of `{ x, y, z, block }` objects (coordinates relative
to the turtle). It returns an **empty array** if no scan-capable module is
equipped or scanning fails — so it's safe to call unconditionally:

```js
for (const hit of turtle.scan()) {
  print(`${hit.block} at ${hit.x},${hit.y},${hit.z}`)
}
```

## Fuel

```js
turtle.getFuelLevel()  // -> remaining fuel (integer)
turtle.refuel(amount)  // add fuel
```

Each move costs 1 fuel; at 0, movement fails. (`refuel` is currently a creative
placeholder until inventory-based refueling lands.)

## Inventory

The turtle has **16 slots, numbered 1–16**.

```js
turtle.select(slot)       // choose the active slot (1..16), used by place()
turtle.getSelectedSlot()  // -> the active slot number
turtle.getItemCount(slot) // -> item count in `slot`; slot <= 0 means the
                          //    selected slot
```

```js
turtle.select(3)
if (turtle.getItemCount(3) > 0) {
  turtle.place()
}
```

## A complete example

```js
// Dig a straight 8-block tunnel, refueling check included.
const LENGTH = 8
for (let i = 0; i < LENGTH; i++) {
  if (turtle.getFuelLevel() <= 0) {
    print("out of fuel at step " + i)
    break
  }
  if (turtle.detect()) turtle.dig()
  if (!turtle.forward()) {
    print("couldn't advance at step " + i)
    break
  }
}
print("done")
```
