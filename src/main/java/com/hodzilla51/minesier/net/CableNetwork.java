package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.SwitchBlockEntity;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.net.CableTopologyCache.NicEndpoint;
import com.hodzilla51.minesier.net.CableTopologyCache.SegmentSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The physical medium for the first network slice.
 *
 * <p>Cables form an undirected shared segment. A frame is offered to every NIC attached to that
 * segment; the NIC decides whether to accept it. Topology is cached per segment and rebuilt only
 * when a cable or NIC block is placed or removed.
 */
public final class CableNetwork {
  private CableNetwork() {}

  /**
   * Offers a frame to the cable segment attached to {@code sourceFace}.
   *
   * @return {@link SendResult#REJECTED} when no cable is attached, {@link SendResult#CONGESTED}
   *     when the segment's per-tick bandwidth budget is exhausted, or {@link SendResult#DELIVERED}
   *     once the frame has been offered to every NIC on the segment.
   */
  public static SendResult send(
      ServerLevel level, BlockPos source, Direction sourceFace, NetworkFrame frame) {
    BlockPos firstCable = source.relative(sourceFace);
    if (!level.hasChunkAt(firstCable) || !isCable(level.getBlockState(firstCable))) {
      return SendResult.REJECTED;
    }

    SegmentSnapshot snapshot = CableTopologyCache.getOrBuild(level, firstCable);
    if (!snapshot.tryConsume(level.getGameTime())) {
      return SendResult.CONGESTED;
    }
    for (NicEndpoint nic : snapshot.nics()) {
      if (!level.hasChunkAt(nic.pos())) continue;
      BlockState state = level.getBlockState(nic.pos());
      if (state.is(ModContent.COMPUTER_BLOCK)
          && level.getBlockEntity(nic.pos()) instanceof ComputerBlockEntity computer) {
        computer.offerFrame(nic.face(), frame);
      } else if (state.is(ModContent.TURTLE_BLOCK)
          && level.getBlockEntity(nic.pos()) instanceof TurtleBlockEntity turtle) {
        turtle.offerFrame(nic.face(), frame);
      } else if (state.is(ModContent.SWITCH_BLOCK)
          && level.getBlockEntity(nic.pos()) instanceof SwitchBlockEntity networkSwitch) {
        networkSwitch.offerFrame(nic.face(), frame);
      }
    }
    return SendResult.DELIVERED;
  }

  private static boolean isCable(BlockState state) {
    return state.is(ModContent.CABLE_BLOCK);
  }
}
