package com.hodzilla51.minesier.js;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The single source of truth for a computer's liveness: the set of currently-live wake/liveness
 * sources, each represented by a {@link RuntimeHandle}.
 *
 * <p>Liveness is asked as a predicate over handles ({@link #isAlive()} / {@link #isResident()} /
 * {@link #hasTickDrivenWork()}) rather than by enumerating "what can wake this computer". A new
 * event source only has to register a handle on start and dispose it on stop; the predicates and
 * {@code stopResident()} never change.
 *
 * <p>Rhino-free on purpose: the registry only manages the life of an opaque handle set, so it can
 * be unit-tested without starting a Rhino {@code Context}. Anything Rhino-aware (the timer's
 * callback, the listener lambda) lives in the {@link JsComputer} handle implementations.
 *
 * <p>Thread-safety: all mutation runs under a private lock so a worker-thread VM (turtles, in #81)
 * and the server thread can touch the registry concurrently. The lock is deliberately <em>not</em>
 * the owning {@code JsComputer} monitor — that keeps {@code stopAllHandles()} from contending with
 * a worker thread that holds the VM monitor across a blocking server hop (see the design's
 * lock-order note). Disposal runs outside the lock because {@code dispose()} may call back (e.g. a
 * compare-and-clear on a NIC listener).
 */
final class LivenessRegistry {

  /** What a handle represents, and whether merely having it makes the computer "resident". */
  enum Kind {
    /** {@code every}/{@code after} timer. Tick-driven; counts as a resident daemon. */
    TIMER(true),
    /** {@code net.onReceive} listener. Frame-driven; counts as a resident daemon. */
    LISTENER(true),
    /**
     * A busy foreground runner (turtle program). Reserved for #81; makes the computer alive/busy
     * but never resident.
     */
    ACTION_RUNNER(false);

    private final boolean residentSource;

    Kind(boolean residentSource) {
      this.residentSource = residentSource;
    }

    boolean isResidentSource() {
      return residentSource;
    }
  }

  /** One live wake/liveness source. Identity is the primary key (handles are single-use). */
  interface RuntimeHandle {
    Kind kind();

    /** Releases whatever external state this handle holds (e.g. clears a NIC listener). */
    void dispose();
  }

  private final Object lock = new Object();
  private final Set<RuntimeHandle> handles = Collections.newSetFromMap(new IdentityHashMap<>());
  private int timerCount;

  /** Registers a live handle. */
  void add(RuntimeHandle handle) {
    synchronized (lock) {
      if (handles.add(handle) && handle.kind() == Kind.TIMER) {
        timerCount++;
      }
    }
  }

  /** Removes a handle without disposing it (the caller owns the lifecycle ordering). */
  void remove(RuntimeHandle handle) {
    synchronized (lock) {
      if (handles.remove(handle) && handle.kind() == Kind.TIMER) {
        timerCount--;
      }
    }
  }

  /** True when any handle is live — resident OR busy. */
  boolean isAlive() {
    synchronized (lock) {
      return !handles.isEmpty();
    }
  }

  /** True when at least one resident-source handle (TIMER/LISTENER) is live. */
  boolean isResident() {
    synchronized (lock) {
      for (RuntimeHandle handle : handles) {
        if (handle.kind().isResidentSource()) {
          return true;
        }
      }
      return false;
    }
  }

  /** True when a TIMER handle exists; an O(1) early-return guard for the per-tick drain. */
  boolean hasTickDrivenWork() {
    synchronized (lock) {
      return timerCount > 0;
    }
  }

  /** A snapshot of the live handles of one kind, safe to iterate outside the lock. */
  List<RuntimeHandle> snapshot(Kind kind) {
    synchronized (lock) {
      List<RuntimeHandle> out = new ArrayList<>();
      for (RuntimeHandle handle : handles) {
        if (handle.kind() == kind) {
          out.add(handle);
        }
      }
      return out;
    }
  }

  /** Disposes every live handle, folding any running daemon. */
  void stopAllHandles() {
    List<RuntimeHandle> snapshot;
    synchronized (lock) {
      snapshot = new ArrayList<>(handles);
      handles.clear();
      timerCount = 0;
    }
    // Dispose outside the lock: dispose() may re-enter (NIC compare-and-clear, etc.).
    for (RuntimeHandle handle : snapshot) {
      handle.dispose();
    }
  }
}
