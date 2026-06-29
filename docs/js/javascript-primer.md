# JavaScript Primer (the absolute basics)

This page is a *thin* tour, just enough to read the rest of the docs. MineSIer
runs ordinary JavaScript, so anything you learn about JS elsewhere applies here.
For real depth, the [MDN JavaScript Guide][mdn-guide] is the canonical resource
and is far better than anything we could rewrite.

> If you already know JavaScript, **skip this page** and read
> [runtime.md](runtime.md) instead — that's where the surprises live.

## Values and variables

```js
let count = 3          // a number; use `let` for values that change
const name = "turtle"  // use `const` for values that don't
let done = false       // a boolean: true / false
let nothing = null     // "no value on purpose"
```

Numbers, strings (`"..."` or `'...'`), and booleans are the everyday types.
Strings can be glued together, and *template literals* let you embed values:

```js
let fuel = 120
print("fuel is " + fuel)      // concatenation
print(`fuel is ${fuel}`)      // template literal — note the backticks
```

## Making decisions

```js
if (fuel > 0) {
  turtle.forward()
} else {
  print("out of fuel")
}
```

Comparisons: `===` (equal), `!==` (not equal), `<`, `>`, `<=`, `>=`. Prefer
`===`/`!==` over `==`/`!=`. Combine conditions with `&&` (and), `||` (or),
`!` (not).

## Repeating things

```js
for (let i = 0; i < 4; i++) {   // run 4 times
  turtle.forward()
}

let n = 0
while (n < 4) {                 // run while the condition holds
  turtle.turnRight()
  n++
}
```

> ⚠️ An accidental infinite loop won't hang Minecraft — the runtime has an
> instruction budget that aborts runaway programs. See
> [runtime.md](runtime.md#the-instruction-budget). To loop *forever on purpose*
> (a daemon), use [`every`](runtime.md#background-timers-every--after), not
> `while (true)`.

## Functions

```js
function mine(times) {
  for (let i = 0; i < times; i++) {
    turtle.dig()
    turtle.forward()
  }
}

mine(5)
```

Arrow functions are the short form, handy for callbacks:

```js
every(20, () => print("tick"))   // see runtime.md
```

## Arrays and objects

```js
let blocks = ["minecraft:dirt", "minecraft:stone"]
print(blocks.length)     // 2
print(blocks[0])         // "minecraft:dirt"
blocks.push("minecraft:sand")

let pos = { x: 10, y: 64, z: -3 }
print(pos.x)             // 10
pos.y = 65
```

Many device calls hand you objects like this (a received network frame, a scan
result) — you read their fields with `.fieldName`.

## What's available

The JavaScript engine is **Rhino 1.8**, which supports modern syntax: `let`/
`const`, arrow functions, template literals, destructuring, `Map`/`Set`,
`Promise`, `JSON.parse`/`JSON.stringify`, and most ES2015+ features. A few
host-environment things you might expect are **deliberately absent or different**
(no `console`, no `setTimeout`, no DOM, no network `fetch`). The replacements
are all in [runtime.md](runtime.md).

[mdn-guide]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide
