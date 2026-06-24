package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

  private static final Map<Level, Set<BlockPos>> MODEMS = new ConcurrentHashMap<>();

  private WirelessNetwork() {}

  public static void register(Level level, BlockPos pos) {
    MODEMS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
  }

  public static void unregister(Level level, BlockPos pos) {
    Set<BlockPos> set = MODEMS.get(level);
    if (set != null) {
      set.remove(pos);
    }
  }

  /**
   * Offers {@code frame} to the computer behind every other modem within range of {@code
   * fromModem}.
   */
  public static void deliver(ServerLevel level, BlockPos fromModem, NetworkFrame frame) {
    Set<BlockPos> modems = MODEMS.get(level);
    if (modems == null) {
      return;
    }
    long rangeSqr = (long) RANGE * RANGE;
    for (BlockPos modem : modems) {
      if (modem.equals(fromModem) || modem.distSqr(fromModem) > rangeSqr) {
        continue;
      }
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
  }
}
