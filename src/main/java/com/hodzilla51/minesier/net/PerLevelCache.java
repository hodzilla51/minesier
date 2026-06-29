package com.hodzilla51.minesier.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.Level;

/** Lifecycle-managed values keyed by loaded {@link Level} instances. */
final class PerLevelCache<V> {
  private final Map<Level, V> values = new ConcurrentHashMap<>();
  private boolean initialized;

  synchronized void init() {
    if (initialized) return;
    initialized = true;
    ServerLevelEvents.UNLOAD.register((server, level) -> values.remove(level));
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> values.clear());
  }

  V get(Level level) {
    return values.get(level);
  }

  V computeIfAbsent(Level level, Function<Level, V> factory) {
    return values.computeIfAbsent(level, factory);
  }

  void remove(Level level, V expected) {
    values.remove(level, expected);
  }
}
