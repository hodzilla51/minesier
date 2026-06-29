package com.hodzilla51.minesier.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure-Java tests for {@link LivenessRegistry} — no Rhino context needed. */
class LivenessRegistryTest {

  /** A handle that records whether it was disposed. */
  private static final class FakeHandle implements LivenessRegistry.RuntimeHandle {
    private final LivenessRegistry.Kind kind;
    boolean disposed;

    FakeHandle(LivenessRegistry.Kind kind) {
      this.kind = kind;
    }

    @Override
    public LivenessRegistry.Kind kind() {
      return kind;
    }

    @Override
    public void dispose() {
      disposed = true;
    }
  }

  @Test
  void emptyRegistryIsNotAlive() {
    LivenessRegistry registry = new LivenessRegistry();
    assertFalse(registry.isAlive());
    assertFalse(registry.isResident());
    assertFalse(registry.hasTickDrivenWork());
  }

  @Test
  void timerHandleIsResidentAndTickDriven() {
    LivenessRegistry registry = new LivenessRegistry();
    registry.add(new FakeHandle(LivenessRegistry.Kind.TIMER));

    assertTrue(registry.isAlive());
    assertTrue(registry.isResident());
    assertTrue(registry.hasTickDrivenWork());
  }

  @Test
  void listenerHandleIsResidentButNotTickDriven() {
    LivenessRegistry registry = new LivenessRegistry();
    registry.add(new FakeHandle(LivenessRegistry.Kind.LISTENER));

    assertTrue(registry.isAlive());
    assertTrue(registry.isResident());
    assertFalse(registry.hasTickDrivenWork());
  }

  @Test
  void actionRunnerIsAliveButNotResident() {
    LivenessRegistry registry = new LivenessRegistry();
    registry.add(new FakeHandle(LivenessRegistry.Kind.ACTION_RUNNER));

    assertTrue(registry.isAlive());
    assertFalse(registry.isResident());
    assertFalse(registry.hasTickDrivenWork());
  }

  @Test
  void removeUnrefsTimerCount() {
    LivenessRegistry registry = new LivenessRegistry();
    FakeHandle timer = new FakeHandle(LivenessRegistry.Kind.TIMER);
    registry.add(timer);
    assertTrue(registry.hasTickDrivenWork());

    registry.remove(timer);
    assertFalse(registry.hasTickDrivenWork());
    assertFalse(registry.isAlive());
    // remove() does not dispose — the caller owns ordering.
    assertFalse(timer.disposed);
  }

  @Test
  void stopAllHandlesDisposesEveryHandleAndEmptiesRegistry() {
    LivenessRegistry registry = new LivenessRegistry();
    FakeHandle timer = new FakeHandle(LivenessRegistry.Kind.TIMER);
    FakeHandle listener = new FakeHandle(LivenessRegistry.Kind.LISTENER);
    registry.add(timer);
    registry.add(listener);

    registry.stopAllHandles();

    assertTrue(timer.disposed);
    assertTrue(listener.disposed);
    assertFalse(registry.isAlive());
    assertFalse(registry.hasTickDrivenWork());
  }

  @Test
  void snapshotReturnsOnlyRequestedKind() {
    LivenessRegistry registry = new LivenessRegistry();
    FakeHandle timer = new FakeHandle(LivenessRegistry.Kind.TIMER);
    registry.add(timer);
    registry.add(new FakeHandle(LivenessRegistry.Kind.LISTENER));

    List<LivenessRegistry.RuntimeHandle> timers = registry.snapshot(LivenessRegistry.Kind.TIMER);
    assertEquals(1, timers.size());
    assertEquals(timer, timers.get(0));
  }
}
