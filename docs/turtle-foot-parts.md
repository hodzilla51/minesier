# Turtle Foot Parts

This document defines the first version of Turtle foot parts. Foot parts are
intended to be a gameplay choice, not a cosmetic variant: players should pick a
movement system based on the job, terrain, and fuel plan.

## Goals

- Make wheels strong on prepared factory routes.
- Make crawlers reliable for outdoor work, mining sites, and rougher terrain.
- Make hover parts strong for air, water, and exploration, while keeping them
  fuel-expensive.
- Avoid strict upgrades. Each foot part should have a clear job where it is the
  best choice.
- Keep the movement model programmable. Players should still write routes with
  explicit `forward`, `back`, `up`, and `down` calls.

## Movement Model

Turtles do not use vanilla falling-block behavior. They do not fall
automatically, and movement is resolved only when a Turtle movement command is
executed.

- `forward` and `back` are horizontal moves only.
- `up` and `down` are vertical moves only.
- Turtles do not automatically climb or descend block-height steps.
- Step handling is left to player programs.
- Air movement is allowed in every direction.
- Non-hover foot parts move very slowly in air and spend more fuel.
- Hover foot parts treat air movement as a normal operating mode.

This keeps the rules simple and avoids special cases where automatic climbing
must also imply automatic falling or descending.

## Terrain Categories

Grounded horizontal movement is classified from the block below the Turtle. The
first version should use vanilla block tags where possible so modded blocks get
reasonable behavior without per-block configuration.

| Category | Source |
| --- | --- |
| `PICKAXE` | `#minecraft:mineable/pickaxe` |
| `SHOVEL` | `#minecraft:mineable/shovel` |
| `AXE` | `#minecraft:mineable/axe` |
| `HOE` | `#minecraft:mineable/hoe` |
| `LIQUID` | Water or other supported liquid states |
| `HAZARD` | Lava, magma, fire-like blocks, or other damaging terrain |
| `OTHER` | Any grounded block that does not match another category |
| `AIRBORNE` | No valid support block below the Turtle |

If a block matches multiple tool tags, the implementation should use a stable
priority order. The initial priority is:

```text
HAZARD > LIQUID > PICKAXE > SHOVEL > AXE > HOE > OTHER > AIRBORNE
```

## Pickaxe Terrain As Tarmac

`PICKAXE` is used as the v1 equivalent of tarmac or hard prepared ground. This
is intentionally broad. The tag is not a road tag; it includes natural stone,
deepslate, ores, utility blocks, machine blocks, and many modded hard blocks.

The tradeoff is acceptable for v1 because:

- Factory floors, stone bricks, concrete-like blocks, metal blocks, and machine
  blocks are likely to be pickaxe-mineable.
- Modded industrial blocks are more likely to work without a custom compatibility
  table.
- Natural cave stone also becomes fast for wheels, but caves still involve more
  vertical movement and airborne recovery, where wheels are weak.
- The rule is easier for players to predict than a large hand-written block
  allowlist.

If this becomes too broad, a later version can add MineSIer-specific terrain
tags such as `#minesier:turtle_terrain/paved` or an exclusion/override tag.

## Foot Parts V1

Foot parts are items equipped into a dedicated Turtle foot slot. A Turtle starts
with no foot part equipped.

The unequipped state is valid and is the default movement profile. It should be
slow enough that players want a real foot part for regular automation, but not
so restrictive that a new Turtle is unusable.

Foot part swapping is not allowed while a Turtle program is running. The
expansion-parts screen should follow the normal Turtle inventory model: players
choose the build before running the program, and the equipped parts remain stable
until the program finishes. This avoids mid-run equipment races as later arm and
top-part slots are added.

### Wheel

Wheels are the factory and prepared-route option.

- Fastest horizontal movement on `PICKAXE` terrain.
- Good fuel efficiency on grounded horizontal routes.
- Poor vertical movement.
- Poor airborne movement.
- Best for flat logistics lines, warehouse loops, and base automation.

### Crawler

Crawlers are the reliable work-site option.

- Strong on `SHOVEL` terrain.
- Stable on mixed outdoor terrain.
- Better vertical movement than wheels.
- Still poor in air.
- Best for mining sites, construction areas, rough outdoor jobs, and gathering
  routes.

### Hover

Hover parts are the exploration and unsupported-movement option.

- Ignores most terrain speed differences.
- Strong in air.
- Strong for vertical movement.
- Expensive fuel cost.
- Best for scouting, water routes, dangerous areas, and routes with frequent
  gaps or height changes.

Hover maintenance fuel while airborne is not part of v1. It can be added later
if hover movement is too strong.

## Initial Movement Costs

These values are initial tuning values, not final balance. `ticks` controls how
long the action takes. `fuel` is the fuel consumed by the action.

Baseline movement is `14 ticks / 1 fuel`.

| Foot part | Movement context | Ticks | Fuel |
| --- | --- | ---: | ---: |
| None | Grounded horizontal movement | 24 | 1 |
| None | Vertical `up` or `down` while grounded | 32 | 2 |
| None | Any airborne movement | 48 | 3 |
| Wheel | Horizontal on `PICKAXE` | 8 | 1 |
| Wheel | Horizontal on `SHOVEL` | 16 | 1 |
| Wheel | Horizontal on `AXE`, `HOE`, or `OTHER` | 14 | 1 |
| Wheel | Vertical `up` or `down` while grounded | 32 | 2 |
| Wheel | Any airborne movement | 40 | 3 |
| Crawler | Horizontal on `PICKAXE` | 14 | 1 |
| Crawler | Horizontal on `SHOVEL` | 11 | 1 |
| Crawler | Horizontal on `AXE`, `HOE`, or `OTHER` | 14 | 1 |
| Crawler | Vertical `up` or `down` while grounded | 22 | 2 |
| Crawler | Any airborne movement | 36 | 3 |
| Hover | Any grounded horizontal movement | 12 | 2 |
| Hover | Vertical `up` or `down` | 12 | 2 |
| Hover | Any airborne movement | 12 | 2 |

`LIQUID` and `HAZARD` need dedicated rules before they are enabled as normal
movement surfaces. Until then, they should fall back to conservative behavior or
be treated as blocked, depending on the movement command and foot part.

## Movement Resolution

The implementation should resolve movement in this order:

1. Determine the requested direction from the Turtle command.
2. Determine whether the destination block can be occupied by the Turtle.
3. Determine whether the Turtle is grounded.
4. If grounded, classify the support block below the Turtle.
5. If not grounded, use `AIRBORNE`.
6. Look up movement ticks and fuel cost from the equipped foot part.
7. Fail the command if fuel is insufficient.
8. Move the Turtle and spend fuel.
9. Use the resolved tick duration for both server pacing and client movement
   animation.

The server remains authoritative. Client animation should receive the resolved
duration from the server instead of assuming a fixed global movement duration.

## GUI Notes

Turtles should have an expansion-parts screen separate from the normal storage
view. The v1 screen only needs a dedicated foot slot, but the layout should leave
room for later arm and top-part slots. Like the normal Turtle inventory, this
screen should not open while a Turtle program is running.

The equipment GUI should communicate practical tradeoffs, not only raw numbers.
The foot part panel should show:

- Movement speed.
- Fuel efficiency.
- Preferred terrain.
- Vertical movement rating.
- Air movement rating.
- Short role tags such as `Factory`, `Worksite`, or `Exploration`.

The foot slot should accept only foot-part items. Removing the item returns the
Turtle to the unequipped movement profile.

When comparing parts, the GUI should make it clear why wheels are excellent on
prepared `PICKAXE` routes, why crawlers are better on rougher jobs, and why hover
parts are strong but fuel-hungry.

## Open Questions

- Exact liquid behavior for water, lava, and modded fluids.
- Exact hazard behavior for magma, fire, cactus, and damaging mod blocks.
- Whether hover should later consume maintenance fuel while airborne.
- Whether carried inventory weight should affect speed or fuel.
- Whether MineSIer should add dedicated terrain tags for more precise factory
  floor support.
- Exact recipe and survival acquisition path. Creative-only access is acceptable
  during the first implementation pass.
- How four-legged and multi-legged foot parts should extend this model in v2.
