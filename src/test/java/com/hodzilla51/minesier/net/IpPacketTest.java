package com.hodzilla51.minesier.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IpPacketTest {
  @Test
  void encodeDecodeRoundTripPreservesFieldsAndUtf8Payload() {
    IpPacket packet = new IpPacket("10.0.0.1", "10.0.0.2", 64, 17, "hello|world\nok");

    IpPacket decoded = IpPacket.decode(packet.encode());

    assertEquals(packet, decoded);
  }

  @Test
  void decodeRejectsNonIpAndMalformedPayloads() {
    assertNull(IpPacket.decode("hello"));
    assertNull(IpPacket.decode("MSIP4|10.0.0.1|10.0.0.2|64|17|not base64!"));
    assertNull(IpPacket.decode("MSIP4|999.0.0.1|10.0.0.2|64|17|eA=="));
  }

  @Test
  void routedDecrementsTtlAndExpiresAtOne() {
    IpPacket packet = new IpPacket("10.0.0.1", "10.0.0.2", 2, 6, "payload");

    assertEquals(1, packet.routed().ttl());
    assertNull(packet.routed().routed());
  }

  @Test
  void constructorRejectsInvalidHeaderFields() {
    assertThrows(
        IllegalArgumentException.class, () -> new IpPacket("1.2.3", "10.0.0.2", 64, 6, ""));
    assertThrows(
        IllegalArgumentException.class, () -> new IpPacket("1.2.3.4", "10.0.0.2", 0, 6, ""));
    assertThrows(
        IllegalArgumentException.class, () -> new IpPacket("1.2.3.4", "10.0.0.2", 64, 256, ""));
  }
}
