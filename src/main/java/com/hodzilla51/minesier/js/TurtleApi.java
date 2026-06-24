package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.net.TurtleVisualAction;

/**
 * The world-facing actions a turtle program can call, exposed to scripts as the {@code turtle}
 * global. Movement returns whether it succeeded; it costs fuel and fails when the tank is empty.
 *
 * <p>Implementations are server-side and authoritative; the VM only ever sees this narrow
 * interface, never Minecraft classes (see the sandbox in {@link JsComputer}).
 */
public interface TurtleApi {
  boolean forward();

  boolean back();

  boolean turnLeft();

  boolean turnRight();

  /** Breaks the block ahead (dropping its items); false if nothing to dig. */
  boolean dig();

  /**
   * Places the named block ({@code "minecraft:stone"}) ahead, creative-style; false if blocked or
   * unknown.
   */
  boolean place(String blockId);

  /**
   * Places the block held in the selected inventory slot ahead, consuming one; false if
   * empty/blocked.
   */
  boolean placeSelected();

  /** Pauses this turtle program for {@code ticks} server ticks without blocking the server. */
  void waitTicks(int ticks);

  /** Selects an inventory slot (1..16) for {@code placeSelected}. */
  void select(int slot);

  /** The currently selected slot (1..16). */
  int getSelectedSlot();

  /** Item count in {@code slot} (1..16), or in the selected slot if {@code slot <= 0}. */
  int getItemCount(int slot);

  /** True if there is a (non-replaceable) block directly ahead. */
  boolean detect();

  /** The id of the block ahead (e.g. {@code "minecraft:dirt"}), or {@code ""} if empty. */
  String inspect();

  /** Remaining fuel; each move costs 1 and movement fails at 0. */
  int getFuelLevel();

  /** Adds fuel (creative placeholder until inventory-based refuel lands). */
  void refuel(int amount);

  /** Emits a renderer-only status effect; used internally for execution errors. */
  void visual(TurtleVisualAction action, String detail);
}
