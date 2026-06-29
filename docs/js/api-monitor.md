# `monitor` — Display Output

The `monitor` global writes text to **Monitor blocks** placed next to a
Computer. You get a handle to one monitor, then write to it.

> `monitor` is available on a Computer; `monitor.at(side)` returns `null` if
> there's no Monitor block on that side.

## Getting a handle

```js
monitor.at(side)   // -> a monitor handle, or null if no monitor on `side`
```

`side` is relative to the computer's screen: `front`, `back`, `left`, `right`,
`up`, `down`.

```js
const m = monitor.at("front")
if (!m) {
  print("no monitor in front")
} else {
  m.setText("Hello!")
}
```

## Handle methods

```js
m.write(text)        // append text (newlines split rows), scrolling old rows off
m.setLine(row, text) // set one row (1-based); false if row out of range
m.setText(text)      // replace the whole buffer (newlines become rows)
m.clear()            // blank the whole screen
m.rows()             // -> number of text rows  (currently 17)
m.columns()          // -> number of columns    (currently 26)
```

All writing methods return `true` on success, `false` if the monitor went away.
The grid is **17 rows × 26 columns**; query it at runtime with `rows()` /
`columns()` rather than hard-coding, in case it changes.

- `write` is append-with-scroll: good for a log.
- `setLine` targets a fixed row: good for a dashboard with stable fields.
- `setText` replaces everything: good for redrawing a whole frame.

## Example: a live dashboard

```js
const m = monitor.at("front")
if (m) {
  m.clear()
  every(20, () => {                 // refresh once per second
    m.setLine(1, "== STATUS ==")
    m.setLine(2, "fuel: " + (typeof turtle !== "undefined"
                              ? turtle.getFuelLevel() : "n/a"))
    m.setLine(3, "back redstone: " + redstone.getInput("back"))
  })
}
```

## Example: scrolling log

```js
const m = monitor.at("up")
let n = 0
every(40, () => {
  m.write("event #" + (++n))   // each call adds a line, scrolling the top off
})
```

The text is drawn at full brightness on the screen face, so it stays readable
even though the block would otherwise self-shadow.
