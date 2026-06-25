# Turtle Arm Parts

This document defines the first version of Turtle arm parts. Unlike foot parts,
arm parts do not need custom MineSIer-specific tool items in v1. The Turtle arm
slot should use normal Minecraft tools directly.

## Goals

- Let players use vanilla and modded tools as Turtle arm equipment.
- Reuse vanilla tool behavior instead of duplicating pickaxe, shovel, axe, and
  hoe rules in MineSIer.
- Make tool tier, durability, harvest suitability, drops, and enchantments matter
  naturally.
- Keep arm equipment focused on block interaction. Movement is handled by foot
  parts.

## Tool Arm Slot

The Turtle has one arm slot in the expansion-parts screen.

- The arm slot accepts tool item stacks.
- A Turtle with an empty arm slot digs as if it has an empty hand.
- `dig` uses the equipped tool stack.
- `place` is not affected by the equipped arm tool.
- Arm swapping is not allowed while a Turtle program is running.
- The equipped arm item should remain stable from program start until the program
  finishes.

The same expansion-parts screen can later contain foot, arm, and top slots, but
v1 only needs to define the arm/tool behavior.

## Vanilla Tool Behavior

Turtle digging should follow vanilla block-breaking behavior for the equipped
tool as much as practical.

The equipped tool should affect:

- Mining speed.
- Whether the tool is suitable for the target block.
- Whether the block can drop its normal loot.
- Tool durability.
- Tool breakage.
- Tool enchantments such as Efficiency, Fortune, Silk Touch, and Unbreaking,
  where the vanilla APIs make them available.

If the tool breaks, the arm slot should be cleared just like a player's broken
tool disappears.

The intent is that a diamond pickaxe in the Turtle arm slot behaves like a
diamond pickaxe, a shovel behaves like a shovel, and a modded mining tool works
through the same vanilla/modded tool hooks instead of requiring a MineSIer
compatibility table.

## Turtle Versus Player Effects

The Turtle should use the equipped tool's behavior, but it is not a player body.
Player-specific conditions should not be copied unless there is a strong reason
to do so.

Examples of effects that are out of scope for v1:

- Haste.
- Mining Fatigue.
- Underwater mining penalties.
- Airborne player mining penalties.
- Player attributes.
- Game mode differences.
- Mending through player XP collection.

This keeps the rule simple: the Turtle uses the tool, but it does not inherit
the player's status or body mechanics.

## Dig Timing

`dig` action duration should be derived from the target block and equipped tool
using vanilla mining speed behavior where possible.

If exact vanilla timing is impractical, the implementation should still preserve
the same relative ordering:

- Correct high-tier tools are fast.
- Correct low-tier tools are slower.
- Incorrect tools are slow.
- Empty hand is slowest for blocks that are not meant to be hand-mined.
- Efficiency should improve speed if supported by the API path used.

The Turtle's `dig` pacing should use the resolved duration, just like foot parts
use resolved movement duration.

## Drops

Block drops should be generated using the equipped tool stack when possible.
This is important for:

- Silk Touch.
- Fortune.
- Blocks that require the correct tool to drop themselves or resources.
- Modded loot behavior that checks the tool stack.

Drops should continue to be inserted into the Turtle inventory, matching the
current Turtle behavior.

## Durability

Successful block breaking should damage the equipped tool through vanilla item
durability behavior when possible.

Unbreaking should reduce durability loss if supported by the API path used. If
the tool reaches zero durability, it should break and the arm slot should become
empty.

## GUI Notes

The expansion-parts screen should show the equipped arm tool and enough
information to make tool state clear:

- Tool name.
- Durability bar.
- Enchantments.
- Whether the slot is empty.

The GUI does not need to explain all vanilla mining rules. Players already know
how tools behave; the screen only needs to make the equipped tool and its
condition visible.

## Open Questions

- Exact item predicate for accepted tools. The preferred direction is to accept
  normal vanilla and modded mining tools without a large MineSIer allowlist.
- Exact fallback formula if vanilla mining duration cannot be reused directly.
- Whether combat weapons should use the arm slot or a future top/weapon slot.
- Whether hoes should get any special Turtle-only farming behavior in a later
  version.
