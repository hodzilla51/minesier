package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.SwitchBlockEntity;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The physical medium for the first network slice.
 *
 * <p>Cables form an undirected shared segment. A frame is offered to every NIC attached to that
 * segment; the NIC decides whether to accept it. Topology is walked at send time for now, which
 * keeps block placement/removal and chunk reloads correct without persisting a separate segment
 * cache.
 */
public final class CableNetwork {
  private static final int MAX_CABLES_PER_SEGMENT = 4_096;

  private CableNetwork() {}

  /**
   * @return whether a cable segment was attached to the source NIC.
   */
  public static boolean send(
      ServerLevel level, BlockPos source, Direction sourceFace, NetworkFrame frame) {
    BlockPos firstCable = source.relative(sourceFace);
    if (!level.hasChunkAt(firstCable) || !isCable(level.getBlockState(firstCable))) {
      return false;
    }

    Set<BlockPos> visited = new HashSet<>();
    ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    pending.add(firstCable);
    while (!pending.isEmpty() && visited.size() < MAX_CABLES_PER_SEGMENT) {
      BlockPos cable = pending.removeFirst();
      if (!visited.add(cable)) {
        continue;
      }
      for (Direction direction : Direction.values()) {
        BlockPos adjacent = cable.relative(direction);
        if (!level.hasChunkAt(adjacent)) {
          continue;
        }
        BlockState state = level.getBlockState(adjacent);
        if (isCable(state)) {
          if (!visited.contains(adjacent)) {
            pending.addLast(adjacent);
          }
        } else if (state.is(ModContent.COMPUTER_BLOCK)
            && level.getBlockEntity(adjacent) instanceof ComputerBlockEntity computer) {
          computer.offerFrame(direction.getOpposite(), frame);
        } else if (state.is(ModContent.TURTLE_BLOCK)
            && level.getBlockEntity(adjacent) instanceof TurtleBlockEntity turtle) {
          turtle.offerFrame(direction.getOpposite(), frame);
        } else if (state.is(ModContent.SWITCH_BLOCK)
            && level.getBlockEntity(adjacent) instanceof SwitchBlockEntity networkSwitch) {
          networkSwitch.offerFrame(direction.getOpposite(), frame);
        }
      }
    }
    return true;
  }

  private static boolean isCable(BlockState state) {
    return state.is(ModContent.CABLE_BLOCK);
  }
}
