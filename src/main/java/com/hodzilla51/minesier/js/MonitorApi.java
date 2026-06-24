package com.hodzilla51.minesier.js;

/**
 * Write access to Monitor blocks adjacent to a computer, exposed to scripts as the {@code monitor}
 * global. Sides are named relative to the computer's screen, matching the {@code net}/{@code
 * redstone} interfaces: {@code front}, {@code back}, {@code left}, {@code right}, {@code up},
 * {@code down}.
 *
 * <p>Implementations are server-side and authoritative; the VM only ever sees this narrow
 * interface, never Minecraft classes (see the sandbox in {@link JsComputer}).
 */
public interface MonitorApi {
  /** True if a Monitor block sits on {@code side}. */
  boolean exists(String side);

  /**
   * Appends text (newlines split into rows), scrolling old rows off the top; false if no monitor.
   */
  boolean write(String side, String text);

  /**
   * Sets row {@code row} (1-based) on the monitor at {@code side}; false if no monitor or bad row.
   */
  boolean setLine(String side, int row, String text);

  /** Replaces the whole buffer on the monitor at {@code side}; false if no monitor. */
  boolean setText(String side, String text);

  /** Clears the monitor at {@code side}; false if no monitor. */
  boolean clear(String side);

  /** Row count of the monitor at {@code side}, or {@code -1} if none. */
  int rows(String side);

  /** Column count of the monitor at {@code side}, or {@code -1} if none. */
  int columns(String side);
}
