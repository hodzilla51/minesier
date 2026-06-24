package com.hodzilla51.minesier;

import com.hodzilla51.minesier.block.ComputerBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Server-side registry of computers running resident timers ({@code every}/{@code after}). Each is
 * ticked once per server tick so its callbacks keep firing after the terminal is closed. A computer
 * is dropped automatically once it has no timers left (or is removed). All access is on the server
 * thread.
 */
public final class ComputerManager {
  private static final Set<ComputerBlockEntity> ACTIVE = new LinkedHashSet<>();

  private ComputerManager() {}

  /** Hooks the per-tick driver. Call once from common init. */
  public static void init() {
    ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());
  }

  /** Registers or unregisters {@code computer} for ticking based on whether it has timers. */
  public static void setActive(ComputerBlockEntity computer, boolean active) {
    if (active) {
      ACTIVE.add(computer);
    } else {
      ACTIVE.remove(computer);
    }
  }

  public static void unregister(ComputerBlockEntity computer) {
    ACTIVE.remove(computer);
  }

  private static void tickAll() {
    if (ACTIVE.isEmpty()) {
      return;
    }
    // Snapshot: a callback may register/unregister computers as it runs.
    List<ComputerBlockEntity> snapshot = new ArrayList<>(ACTIVE);
    for (ComputerBlockEntity computer : snapshot) {
      if (computer.isRemoved() || !computer.tickDaemon()) {
        ACTIVE.remove(computer);
      }
    }
  }
}
