package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.ModContent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-level cache of cable segment topology.
 *
 * <p>Without this cache, {@link CableNetwork#send} runs a full BFS on every frame — O(cables) per
 * send. This class caches each segment's NIC endpoint list so the BFS only runs on topology change
 * (block placed/removed).
 *
 * <p>Callers must call {@link #invalidateAt(Level, BlockPos)} from the {@code onPlace}/{@code
 * onRemove} hooks of every block that participates in the cable network.
 */
public final class CableTopologyCache {
  private static final int MAX_CABLES_PER_SEGMENT = 4_096;

  // Keyed by Level reference (object identity — Level doesn't override equals/hashCode).
  private static final Map<Level, CableTopologyCache> CACHES = new HashMap<>();
  private static boolean initialized;

  // Maps every cable BlockPos in a segment to that segment's snapshot (shared reference).
  private final Map<BlockPos, SegmentSnapshot> byPos = new HashMap<>();

  private CableTopologyCache() {}

  public static void init() {
    if (initialized) return;
    initialized = true;
    // Free a dimension's cache when it unloads mid-session; SERVER_STOPPED is the catch-all.
    ServerLevelEvents.UNLOAD.register((server, level) -> CACHES.remove(level));
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHES.clear());
  }

  /**
   * Returns the cached topology for the segment reachable from {@code firstCable}, building it via
   * BFS on a cache miss.
   */
  static SegmentSnapshot getOrBuild(ServerLevel level, BlockPos firstCable) {
    CableTopologyCache cache = CACHES.computeIfAbsent(level, l -> new CableTopologyCache());
    SegmentSnapshot cached = cache.byPos.get(firstCable);
    if (cached != null) return cached;
    SegmentSnapshot snapshot = build(level, firstCable);
    // A snapshot that stopped at an unloaded chunk is partial: it omits NICs across the boundary.
    // Caching it would make those NICs permanently unreachable (nothing re-fires invalidation when
    // the chunk later loads), so leave it uncached and rebuild on the next send — exactly what the
    // pre-cache code did, while still caching the common fully-loaded case.
    if (snapshot.complete()) {
      for (BlockPos cable : snapshot.cables()) {
        cache.byPos.put(cable, snapshot);
      }
    }
    return snapshot;
  }

  /**
   * Drops the cached segment(s) at and adjacent to {@code pos}. Call this whenever a cable or NIC
   * block is placed or removed.
   */
  public static void invalidateAt(Level level, BlockPos pos) {
    if (level.isClientSide()) return;
    CableTopologyCache cache = CACHES.get(level);
    if (cache == null) return;
    cache.removeSegmentAt(pos);
    for (Direction d : Direction.values()) {
      cache.removeSegmentAt(pos.relative(d));
    }
  }

  private void removeSegmentAt(BlockPos pos) {
    SegmentSnapshot seg = byPos.remove(pos);
    if (seg == null) return;
    for (BlockPos cable : seg.cables()) {
      byPos.remove(cable);
    }
  }

  private static SegmentSnapshot build(ServerLevel level, BlockPos firstCable) {
    Set<BlockPos> cables = new HashSet<>();
    List<NicEndpoint> nics = new ArrayList<>();
    ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    pending.add(firstCable);
    boolean complete = true;
    while (!pending.isEmpty() && cables.size() < MAX_CABLES_PER_SEGMENT) {
      BlockPos cable = pending.removeFirst();
      if (!cables.add(cable.immutable())) continue;
      for (Direction d : Direction.values()) {
        BlockPos adjacent = cable.relative(d);
        if (!level.hasChunkAt(adjacent)) {
          complete = false; // BFS truncated at a chunk edge; the snapshot may be missing NICs.
          continue;
        }
        BlockState state = level.getBlockState(adjacent);
        if (state.is(ModContent.CABLE_BLOCK)) {
          if (!cables.contains(adjacent)) pending.addLast(adjacent);
        } else if (state.is(ModContent.COMPUTER_BLOCK)
            || state.is(ModContent.TURTLE_BLOCK)
            || state.is(ModContent.SWITCH_BLOCK)) {
          nics.add(new NicEndpoint(adjacent.immutable(), d.getOpposite()));
        }
      }
    }
    return new SegmentSnapshot(Set.copyOf(cables), List.copyOf(nics), complete);
  }

  record NicEndpoint(BlockPos pos, Direction face) {}

  static final class SegmentSnapshot {
    private final Set<BlockPos> cables;
    private final List<NicEndpoint> nics;
    private final boolean complete;
    private long lastResetTick = -1;
    private int framesThisTick = 0;

    SegmentSnapshot(Set<BlockPos> cables, List<NicEndpoint> nics, boolean complete) {
      this.cables = cables;
      this.nics = nics;
      this.complete = complete;
    }

    Set<BlockPos> cables() {
      return cables;
    }

    List<NicEndpoint> nics() {
      return nics;
    }

    /** Whether the BFS reached every cable without truncating at an unloaded chunk. */
    boolean complete() {
      return complete;
    }

    /**
     * Consumes one frame slot for the given game tick. Returns {@code false} when the per-segment
     * bandwidth budget ({@link MineSIerConfig#segmentFramesPerTick}) is exhausted for this tick.
     *
     * <p>The reset is lazy: the counter clears automatically on the first call of a new tick, so no
     * separate tick-event registration is needed.
     */
    synchronized boolean tryConsume(long gameTick) {
      if (lastResetTick != gameTick) {
        lastResetTick = gameTick;
        framesThisTick = 0;
      }
      if (framesThisTick >= MineSIerConfig.segmentFramesPerTick) return false;
      framesThisTick++;
      return true;
    }
  }
}
