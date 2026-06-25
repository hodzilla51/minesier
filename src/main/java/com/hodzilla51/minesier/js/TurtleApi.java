package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.net.TurtleVisualAction;
import java.util.List;

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

  boolean up();

  boolean down();

  boolean turnLeft();

  boolean turnRight();

  /** Breaks the block ahead (dropping its items); false if nothing to dig. */
  boolean dig();

  /**
   * Places the selected block ahead only when it matches {@code blockId}; consumes one item and
   * returns false when the slot is empty, mismatched, or blocked.
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

  /** Top-module scan result. Empty when no scan-capable module is equipped or scanning fails. */
  default List<ScanResult> scan() {
    return List.of();
  }

  /** Emits a renderer-only status effect; used internally for execution errors. */
  void visual(TurtleVisualAction action, String detail);

  /** Server-side pacing hook; not exposed to JavaScript. */
  default int actionTicks(String op, Object[] args, int defaultTicks) {
    return defaultTicks;
  }

  /** Server-side action progress hook; not exposed to JavaScript. */
  default void actionProgress(String op, Object[] args, int elapsedTicks, int totalTicks) {}

  /** Clears any server-side progress visuals for the action. */
  default void clearActionProgress(String op, Object[] args) {}

  record ScanResult(int x, int y, int z, String block) {}
}
