package com.hodzilla51.minesier.turtle;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.js.NetworkApi;
import com.hodzilla51.minesier.net.CableNetwork;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkListener;
import com.hodzilla51.minesier.net.NetworkManager;
import com.hodzilla51.minesier.net.TurtleVisualAction;
import com.hodzilla51.minesier.net.TurtleVisualS2C;
import com.hodzilla51.minesier.net.WirelessNetwork;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Network identity and live NIC state carried with a turtle while its block entity hops cells.
 *
 * <p>Turtle JavaScript runs on a worker thread. Operations that inspect the world or inject a frame
 * therefore synchronously hop to the server thread; receive callbacks already run there.
 */
public final class TurtleNetworkState implements NetworkApi {
  private static final int MAX_INBOX_FRAMES = 64;
  private static final int MAX_FRAME_BYTES = 4 * 1024;
  private static final long SERVER_HOP_TIMEOUT_MS = 2_000L;

  private final EnumMap<Direction, NicState> nics = new EnumMap<>(Direction.class);
  private volatile ServerLevel level;
  private volatile BlockPos pos;
  private volatile Direction facing = Direction.NORTH;
  private String networkAddress = formatAddress(UUID.randomUUID());

  public TurtleNetworkState() {
    for (Direction direction : Direction.values()) {
      nics.put(direction, new NicState());
    }
  }

  public void attach(ServerLevel level, BlockPos pos, Direction facing) {
    this.level = level;
    this.pos = pos.immutable();
    this.facing = facing;
  }

  public String getNetworkAddress() {
    return networkAddress;
  }

  public void setNetworkAddress(String networkAddress) {
    if (networkAddress != null && !networkAddress.isBlank()) {
      this.networkAddress = networkAddress;
    }
  }

  /** Called by a cable segment on the server thread. */
  public void offerFrame(Direction face, NetworkFrame frame) {
    NicState nic = nics.get(face);
    boolean addressedToMe =
        addressFor(face).equals(frame.destination())
            || NetworkFrame.BROADCAST.equals(frame.destination());
    if (nic == null || (!nic.promiscuous && !addressedToMe)) {
      return;
    }
    emitVisual(TurtleVisualAction.NET_RECEIVE, "@>.[]");
    NetworkListener listener = nic.listener;
    if (listener != null) {
      NetworkManager.schedule(
          queuedFrame -> {
            if (nic.listener == listener) {
              listener.onFrame(queuedFrame);
            }
          },
          frame);
      return;
    }
    if (nic.inbox.size() < MAX_INBOX_FRAMES) {
      nic.inbox.addLast(frame);
    }
  }

  @Override
  public String address() {
    return addressFor(legacyFace());
  }

  @Override
  public boolean send(String destination, String data) {
    return send(legacyFace(), destination, data);
  }

  @Override
  public NetworkFrame receive() {
    return receiveFrame(legacyFace());
  }

  @Override
  public String address(String interfaceName) {
    Direction face = parseFace(interfaceName);
    return face == null ? null : addressFor(face);
  }

  @Override
  public boolean send(String interfaceName, String destination, String data) {
    Direction face = parseFace(interfaceName);
    return face != null && send(face, destination, data);
  }

  @Override
  public NetworkFrame receive(String interfaceName) {
    Direction face = parseFace(interfaceName);
    return face == null ? null : receiveFrame(face);
  }

  @Override
  public boolean forward(String interfaceName, NetworkFrame frame) {
    Direction face = parseFace(interfaceName);
    if (face == null
        || frame.destination().isBlank()
        || frame.data().getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
      return false;
    }
    NetworkFrame forwarded = frame.nextHop();
    if (forwarded == null) {
      return false;
    }
    return onServer(() -> emit(face, forwarded, TurtleVisualAction.NET_FORWARD, ">.>"), false);
  }

  @Override
  public boolean setPromiscuous(String interfaceName, boolean enabled) {
    Direction face = parseFace(interfaceName);
    NicState nic = face == null ? null : nics.get(face);
    if (nic == null) {
      return false;
    }
    nic.promiscuous = enabled;
    return true;
  }

  @Override
  public boolean setReceiveListener(String interfaceName, NetworkListener listener) {
    Direction face = parseFace(interfaceName);
    NicState nic = face == null ? null : nics.get(face);
    if (nic == null) {
      return false;
    }
    nic.listener = listener;
    return true;
  }

  @Override
  public boolean clearReceiveListener(String interfaceName) {
    Direction face = parseFace(interfaceName);
    NicState nic = face == null ? null : nics.get(face);
    if (nic == null) {
      return false;
    }
    nic.listener = null;
    return true;
  }

  @Override
  public void clearReceiveListeners() {
    for (NicState nic : nics.values()) {
      nic.listener = null;
    }
  }

  @Override
  public void reportOutput(List<String> lines) {
    if (lines.isEmpty()) {
      return;
    }
    onServer(
        () -> {
          ServerLevel currentLevel = level;
          BlockPos currentPos = pos;
          if (currentLevel != null
              && currentPos != null
              && currentLevel.getBlockEntity(currentPos) instanceof TurtleBlockEntity turtle) {
            turtle.appendNetworkOutput(lines);
          }
          return true;
        },
        false);
  }

  private boolean send(Direction face, String destination, String data) {
    if (destination.isBlank() || data.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
      return false;
    }
    return onServer(
        () ->
            emit(
                face,
                new NetworkFrame(addressFor(face), destination, data),
                TurtleVisualAction.NET_SEND,
                "[].>@"),
        false);
  }

  private NetworkFrame receiveFrame(Direction face) {
    NicState nic = nics.get(face);
    return nic == null ? null : nic.inbox.pollFirst();
  }

  private boolean emit(
      Direction face, NetworkFrame frame, TurtleVisualAction visualAction, String visualDetail) {
    ServerLevel currentLevel = level;
    BlockPos currentPos = pos;
    if (currentLevel == null || currentPos == null) {
      return false;
    }
    BlockPos adjacent = currentPos.relative(face);
    boolean sent;
    if (currentLevel.getBlockState(adjacent).is(ModContent.WIRELESS_MODEM_BLOCK)) {
      WirelessNetwork.deliver(currentLevel, adjacent, frame);
      sent = true;
    } else {
      sent = CableNetwork.send(currentLevel, currentPos, face, frame);
    }
    if (sent) {
      emitVisual(visualAction, visualDetail);
    }
    return sent;
  }

  private Direction legacyFace() {
    return facing.getOpposite();
  }

  private Direction parseFace(String name) {
    Direction front = facing;
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "front", "forward" -> front;
      case "back" -> front.getOpposite();
      case "left" -> front.getCounterClockWise();
      case "right" -> front.getClockWise();
      case "up" -> Direction.UP;
      case "down" -> Direction.DOWN;
      default -> null;
    };
  }

  private String addressFor(Direction face) {
    if (face == legacyFace()) {
      return networkAddress;
    }
    return formatAddress(
        UUID.nameUUIDFromBytes(
            (networkAddress + "/" + face.getSerializedName()).getBytes(StandardCharsets.UTF_8)));
  }

  private void emitVisual(TurtleVisualAction action, String detail) {
    ServerLevel currentLevel = level;
    BlockPos currentPos = pos;
    if (currentLevel == null || currentPos == null) {
      return;
    }
    TurtleVisualS2C payload = new TurtleVisualS2C(currentPos, action, detail);
    for (var player : PlayerLookup.tracking(currentLevel, currentPos)) {
      ServerPlayNetworking.send(player, payload);
    }
  }

  private <T> T onServer(ServerCall<T> action, T fallback) {
    ServerLevel currentLevel = level;
    if (currentLevel == null) {
      return fallback;
    }
    if (currentLevel.getServer().isSameThread()) {
      return action.run();
    }
    CompletableFuture<T> result = new CompletableFuture<>();
    currentLevel
        .getServer()
        .execute(
            () -> {
              try {
                result.complete(action.run());
              } catch (RuntimeException error) {
                result.completeExceptionally(error);
              }
            });
    try {
      return result.get(SERVER_HOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String formatAddress(UUID uuid) {
    long value = uuid.getLeastSignificantBits();
    StringBuilder result = new StringBuilder(17);
    for (int i = 5; i >= 0; i--) {
      if (result.length() > 0) {
        result.append(':');
      }
      int octet = (int) (value >>> (i * 8)) & 0xff;
      if (i == 5) {
        octet = (octet & 0xfe) | 0x02;
      }
      result.append(String.format("%02x", octet));
    }
    return result.toString();
  }

  @FunctionalInterface
  private interface ServerCall<T> {
    T run();
  }

  private static final class NicState {
    final ConcurrentLinkedDeque<NetworkFrame> inbox = new ConcurrentLinkedDeque<>();
    volatile boolean promiscuous;
    volatile NetworkListener listener;
  }
}
