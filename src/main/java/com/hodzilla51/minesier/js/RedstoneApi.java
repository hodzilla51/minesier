package com.hodzilla51.minesier.js;

/**
 * The redstone I/O a computer program can drive, exposed to scripts as the {@code redstone} global.
 *
 * <p>Sides are named relative to the computer's screen, matching the {@code net} interface names:
 * {@code front}, {@code back}, {@code left}, {@code right}, {@code up}, {@code down}. Levels are
 * the vanilla analog range 0..15.
 *
 * <p>Implementations are server-side and authoritative; the VM only ever sees this narrow
 * interface, never Minecraft classes (see the sandbox in {@link JsComputer}).
 */
public interface RedstoneApi {
  /**
   * Analog signal strength (0..15) currently entering {@code side}, or {@code -1} if the side name
   * is unknown.
   */
  int getInput(String side);

  /** Analog level (0..15) this computer is emitting on {@code side}, or {@code -1} if unknown. */
  int getOutput(String side);

  /**
   * Sets the analog output on {@code side}, clamped to 0..15; neighboring blocks are notified.
   * Returns false if the side name is unknown.
   */
  boolean setOutput(String side, int level);

  /** The valid side names, in a stable order, for {@code redstone.getSides()}. */
  String[] sides();
}
