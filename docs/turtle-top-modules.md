# Turtle Top Modules

This document defines the first version of Turtle top modules. The top slot is
intended to be the main extension point for sensing, communication, passive
utilities, and later combat or automation modules.

The v1 goal is not to complete every top-module idea. The goal is to build a
small, real module system that can safely accept more modules later.

## Goals

- Add a dedicated top slot to the Turtle expansion-parts screen.
- Keep top modules separate from foot movement and arm tool behavior.
- Provide a stable foundation for future utility, sensor, communication, and
  weapon modules.
- Ship one useful v1 module to validate the slot, API extension path, pacing,
  fuel cost, and GUI display.

## Top Slot

The Turtle has one top slot in the expansion-parts screen.

- The top slot accepts dedicated top-module items.
- A Turtle with an empty top slot has no top-module abilities.
- Top-module swapping is not allowed while a Turtle program is running.
- The equipped top module should remain stable from program start until the
  program finishes.

Top modules may provide:

- Turtle API methods.
- Passive tick behavior.
- Range-based actions.
- Cooldowns or action pacing.
- Fuel costs.
- GUI status data.

v1 should implement the slot and one module, then keep the rest of the module
space open for later additions.

## V1 Module: Proximity Sensor Module

The Proximity Sensor Module is the first top module. It gives the Turtle a short
range scan of nearby blocks.

The module adds:

```js
turtle.scan()
```

`scan` returns nearby non-air blocks using positions relative to the Turtle.

## Scan Range

The v1 scan volume is a small box centered on the Turtle:

- Horizontal radius: `3`.
- Vertical range: `1` block up and `1` block down.
- Total checked volume: `7 x 3 x 7`.
- Maximum checked block positions: `147`.

This is intentionally larger than `inspect`, but still small enough to tune
after playtesting. If the module is too strong or too expensive, the scan volume
can be reduced before adding more sensor modules.

## Scan Result

`scan` should return non-air blocks by default. Each result entry should include
at least:

```js
{
  x: -2,
  y: 0,
  z: 1,
  block: "minecraft:stone"
}
```

The coordinates are relative to the Turtle position:

- `x`: world-relative east/west offset.
- `y`: vertical offset.
- `z`: world-relative north/south offset.

The first version should use world-relative offsets rather than facing-relative
`front`, `right`, and `up` offsets. Facing-relative scan output can be added
later if it proves useful.

## Scan Rules

- `scan` does not require line of sight.
- `scan` does not scan unloaded chunks.
- `scan` returns block IDs, not special ore classifications.
- `scan` should not include air blocks in v1.
- `scan` should use action pacing like other Turtle operations.
- `scan` should consume fuel.

The lack of line-of-sight means the module can detect nearby ores or hidden
blocks. This is acceptable for v1 because the range is small, the module uses a
dedicated top slot, and each scan costs time and fuel.

## Initial Scan Cost

These values are initial tuning values:

| Action | Ticks | Fuel |
| --- | ---: | ---: |
| `scan` | 20 | 1 |

If scan output is too powerful in practice, reduce the scan volume before adding
complex filtering rules.

## GUI Notes

The expansion-parts screen should show:

- Equipped top module name.
- Whether the slot is empty.
- Module role, such as `Sensor`.
- Main action summary, such as `Scan nearby blocks`.
- Initial range summary, such as `7 x 3 x 7`.

The GUI should leave space for future top module status fields, such as
cooldown, range, charge, ammunition, or signal strength.

## Future Module Directions

Future top modules can build on the same slot and module hooks:

- Radar modules for entity detection.
- Ore scanner modules with stricter range or filtering rules.
- Antenna modules for wireless/network range.
- Light modules.
- Shield modules.
- Turrets or other weapon modules.
- Route, marker, or mapping modules.

Combat modules are intentionally out of scope for v1.

## Open Questions

- Exact item/data structure used to register top modules.
- Exact API binding path for module-provided Turtle methods.
- Whether scan results should eventually support facing-relative coordinates.
- Whether scan should later support options such as `includeAir`.
- Whether fuel cost should scale with result count or checked volume.
