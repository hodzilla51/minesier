package com.hodzilla51.minesier.js;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * Global Rhino factory enforcing MineSIer's runtime guarantees on every script context: interpreted
 * mode (Knot-classloader-safe) and an instruction budget that aborts runaway code (e.g. {@code
 * while(true){}}) — the anti-runaway half of the "lightweight / no server freeze" requirement,
 * complementing fuel limits.
 */
public final class SafeContextFactory extends ContextFactory {
  /** Observe (and check the budget) every this many interpreted instructions. */
  private static final int OBSERVE_EVERY = 100_000;

  /** Hard per-run instruction cap; exceeding it kills the script. */
  private static final long MAX_INSTRUCTIONS = 200_000_000L;

  private static final ThreadLocal<long[]> COUNT = ThreadLocal.withInitial(() -> new long[1]);
  private static final ThreadLocal<Long> LIMIT = ThreadLocal.withInitial(() -> MAX_INSTRUCTIONS);

  private SafeContextFactory() {}

  /** Installs this as the global factory (idempotent). Call once, before any script runs. */
  public static void install() {
    if (!ContextFactory.hasExplicitGlobal()) {
      ContextFactory.initGlobal(new SafeContextFactory());
    }
  }

  /** Resets the calling thread's instruction budget; call at the start of each run. */
  public static void resetCounter() {
    COUNT.get()[0] = 0L;
    LIMIT.set(MAX_INSTRUCTIONS);
  }

  /** Resets the calling thread with a tighter cap for an event callback. */
  public static void resetCounter(long instructionLimit) {
    COUNT.get()[0] = 0L;
    LIMIT.set(instructionLimit);
  }

  @Override
  protected void onContextCreated(Context cx) {
    super.onContextCreated(cx);
    cx.setInterpretedMode(true);
    cx.setInstructionObserverThreshold(OBSERVE_EVERY);
  }

  @Override
  protected void observeInstructionCount(Context cx, int instructionCount) {
    long[] c = COUNT.get();
    c[0] += instructionCount;
    if (c[0] > LIMIT.get()) {
      throw new IllegalStateException("script exceeded instruction limit (runaway?)");
    }
  }
}
