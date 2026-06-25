package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.MineSIerConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Tick-budgeted dispatcher for network receive handlers.
 *
 * <p>Today every caller is on the server thread (computer VMs run synchronously and {@link
 * #dispatch()} runs on the server tick). The queue is still guarded so a future off-thread producer
 * cannot corrupt it. Events run OUTSIDE the lock so a handler that schedules more work can't
 * deadlock or re-enter the queue ops.
 */
public final class NetworkManager {
  private static final Object LOCK = new Object();
  private static final Deque<Runnable> EVENTS = new ArrayDeque<>();

  private NetworkManager() {}

  public static void init() {
    ServerTickEvents.END_SERVER_TICK.register(server -> dispatch());
  }

  /** Schedules a receive handler for a frame. Returns false when backpressure drops the event. */
  public static boolean schedule(NetworkListener listener, NetworkFrame frame) {
    return schedule(() -> listener.onFrame(frame));
  }

  /** Schedules a bounded data-plane action for the next server tick. */
  public static boolean schedule(Runnable action) {
    synchronized (LOCK) {
      if (EVENTS.size() >= MineSIerConfig.maxNetworkQueuedEvents) {
        return false;
      }
      EVENTS.addLast(action);
      return true;
    }
  }

  private static void dispatch() {
    for (int processed = 0; processed < MineSIerConfig.maxNetworkEventsPerTick; processed++) {
      Runnable event;
      synchronized (LOCK) {
        event = EVENTS.pollFirst();
      }
      if (event == null) {
        return;
      }
      try {
        event.run();
      } catch (Exception ignored) {
        // Script errors are reported through the owning computer's transcript.
      }
    }
  }
}
