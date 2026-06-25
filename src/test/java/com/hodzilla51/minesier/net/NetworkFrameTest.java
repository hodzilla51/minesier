package com.hodzilla51.minesier.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NetworkFrameTest {
  @Test
  void nextHopIncrementsUntilLimit() {
    NetworkFrame frame = new NetworkFrame("a", "b", "payload");

    for (int i = 1; i < NetworkFrame.MAX_HOPS; i++) {
      frame = frame.nextHop();
      assertEquals(i, frame.hops());
    }

    assertNull(frame.nextHop());
  }

  @Test
  void broadcastAddressIsStable() {
    assertEquals("ff:ff:ff:ff:ff:ff", NetworkFrame.BROADCAST);
  }
}
