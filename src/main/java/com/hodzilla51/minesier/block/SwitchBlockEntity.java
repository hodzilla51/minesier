package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.CableNetwork;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkManager;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A six-port, store-and-forward learning switch with a bounded dynamic MAC table. */
public class SwitchBlockEntity extends BlockEntity {
  private static final int MAX_MAC_ENTRIES = 256;
  private static final long MAC_AGE_TICKS = 6_000; // Five minutes at 20 ticks per second.
  private final Map<String, MacEntry> macTable =
      new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MacEntry> eldest) {
          return size() > MAX_MAC_ENTRIES;
        }
      };

  public SwitchBlockEntity(BlockPos pos, BlockState state) {
    super(ModContent.SWITCH_BLOCK_ENTITY, pos, state);
  }

  /**
   * Called by a cable at the physical ingress port. Forwarding is deferred to the tick dispatcher.
   */
  public void offerFrame(Direction ingress, NetworkFrame frame) {
    NetworkManager.schedule(() -> forward(ingress, frame));
  }

  private void forward(Direction ingress, NetworkFrame frame) {
    if (isRemoved() || !(level instanceof ServerLevel serverLevel)) {
      return;
    }
    long now = serverLevel.getGameTime();
    macTable.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick() > MAC_AGE_TICKS);
    macTable.put(frame.source(), new MacEntry(ingress, now));

    // Advance one hop; drop if the frame has been forwarded too many times (loop guard).
    NetworkFrame forwarded = frame.nextHop();
    if (forwarded == null) {
      return;
    }

    MacEntry destination = macTable.get(frame.destination());
    Direction egress = destination == null ? null : destination.port();
    if (egress != null) {
      if (egress != ingress) {
        CableNetwork.send(serverLevel, worldPosition, egress, forwarded);
      }
      return;
    }

    for (Direction direction : Direction.values()) {
      if (direction != ingress) {
        CableNetwork.send(serverLevel, worldPosition, direction, forwarded);
      }
    }
  }

  private record MacEntry(Direction port, long lastSeenTick) {}
}
