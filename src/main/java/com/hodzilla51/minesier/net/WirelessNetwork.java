package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The wireless medium: a shared, range-limited broadcast.
 *
 * <p>Wireless modems register their position per level (on load) so a transmission doesn't have to
 * scan the world. A frame sent from one modem is offered to the computer attached to every OTHER
 * modem within {@link #RANGE} blocks — like real radio, everyone in range hears it, which is
 * exactly what makes promiscuous wireless sniffing possible.
 */
public final class WirelessNetwork {
  /** Broadcast range in blocks (near-field). */
  public static final int RANGE = 16;

  private static final Map<Level, Map<Cell, Set<BlockPos>>> MODEMS = new ConcurrentHashMap<>();
  private static boolean initialized;

  private WirelessNetwork() {}

  public static void init() {
    if (initialized) {
      return;
    }
    initialized = true;
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> MODEMS.clear());
  }

  public static void register(Level level, BlockPos pos) {
    MODEMS
        .computeIfAbsent(level, l -> new ConcurrentHashMap<>())
        .computeIfAbsent(Cell.from(pos), ignored -> ConcurrentHashMap.newKeySet())
        .add(pos.immutable());
  }

  public static void unregister(Level level, BlockPos pos) {
    Map<Cell, Set<BlockPos>> cells = MODEMS.get(level);
    if (cells == null) {
      return;
    }
    Cell cell = Cell.from(pos);
    Set<BlockPos> set = cells.get(cell);
    if (set == null) {
      return;
    }
    set.remove(pos);
    if (set.isEmpty()) {
      cells.remove(cell, set);
    }
    if (cells.isEmpty()) {
      MODEMS.remove(level, cells);
    }
  }

  /**
   * Offers {@code frame} to the computer behind every other modem within range of {@code
   * fromModem}.
   */
  public static void deliver(ServerLevel level, BlockPos fromModem, NetworkFrame frame) {
    Map<Cell, Set<BlockPos>> cells = MODEMS.get(level);
    if (cells == null) {
      return;
    }
    long rangeSqr = (long) RANGE * RANGE;
    Cell center = Cell.from(fromModem);
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -1; dz <= 1; dz++) {
          Set<BlockPos> modems = cells.get(center.offset(dx, dy, dz));
          if (modems == null) {
            continue;
          }
          for (BlockPos modem : modems) {
            if (modem.equals(fromModem) || modem.distSqr(fromModem) > rangeSqr) {
              continue;
            }
            deliverToAttachedDevice(level, modem, frame);
          }
        }
      }
    }
  }

  private static void deliverToAttachedDevice(
      ServerLevel level, BlockPos modem, NetworkFrame frame) {
    for (Direction direction : Direction.values()) {
      BlockPos neighbor = modem.relative(direction);
      if (level.getBlockEntity(neighbor) instanceof ComputerBlockEntity computer) {
        // The computer's face that touches this modem is the opposite of modem->neighbor.
        computer.offerFrame(direction.getOpposite(), frame);
      } else if (level.getBlockEntity(neighbor) instanceof TurtleBlockEntity turtle) {
        turtle.offerFrame(direction.getOpposite(), frame);
      }
    }
  }

  private record Cell(int x, int y, int z) {
    static Cell from(BlockPos pos) {
      return new Cell(
          Math.floorDiv(pos.getX(), RANGE),
          Math.floorDiv(pos.getY(), RANGE),
          Math.floorDiv(pos.getZ(), RANGE));
    }

    Cell offset(int dx, int dy, int dz) {
      return new Cell(x + dx, y + dy, z + dz);
    }
  }
}
