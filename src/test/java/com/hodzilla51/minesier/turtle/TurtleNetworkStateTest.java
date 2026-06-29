package com.hodzilla51.minesier.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hodzilla51.minesier.net.NetworkFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class TurtleNetworkStateTest {
  @Test
  void baseAddressAndInboxSurviveMovement() {
    TurtleNetworkState network = new TurtleNetworkState();
    network.setNetworkAddress("02:00:00:00:00:01");
    network.attach(null, BlockPos.ZERO, Direction.NORTH);

    String base = network.address();
    network.offerFrame(Direction.SOUTH, new NetworkFrame("sender", base, "before move"));

    network.attach(null, new BlockPos(5, 70, -2), Direction.NORTH);

    assertEquals(base, network.address());
    NetworkFrame received = network.receive();
    assertNotNull(received);
    assertEquals("before move", received.data());
    assertNull(network.receive());
  }

  @Test
  void relativeNicAddressesMoveWithFacingRules() {
    TurtleNetworkState network = new TurtleNetworkState();
    network.setNetworkAddress("02:00:00:00:00:02");
    network.attach(null, BlockPos.ZERO, Direction.NORTH);

    String northFront = network.address("front");
    String northBack = network.address("back");

    network.attach(null, BlockPos.ZERO, Direction.EAST);

    assertEquals(northFront, network.address("left"));
    assertEquals(northBack, network.address("back"));
  }
}
