package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkListener;
import com.hodzilla51.minesier.net.SendResult;
import java.util.List;

/** Narrow, Minecraft-free interface exposed to the {@code net} JavaScript global. */
public interface NetworkApi {
  String address();

  /** Injects one frame into the locally connected cable segment. */
  SendResult send(String destination, String data);

  /** Returns the next frame accepted by this NIC, or {@code null} when none is queued. */
  NetworkFrame receive();

  /** Returns an interface address, or {@code null} for an unknown interface name. */
  String address(String interfaceName);

  SendResult send(String interfaceName, String destination, String data);

  NetworkFrame receive(String interfaceName);

  SendResult forward(String interfaceName, NetworkFrame frame);

  boolean setPromiscuous(String interfaceName, boolean enabled);

  boolean setReceiveListener(String interfaceName, NetworkListener listener);

  boolean clearReceiveListener(String interfaceName);

  void clearReceiveListeners();

  void reportOutput(List<String> lines);
}
