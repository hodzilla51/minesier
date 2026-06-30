package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.CableNetwork;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkManager;
import com.hodzilla51.minesier.net.SendResult;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A six-port, store-and-forward learning switch with a bounded dynamic MAC table. */
public class SwitchBlockEntity extends BlockEntity implements AccessControlledBlockEntity {
  private static final int MAX_MAC_ENTRIES = 256;
  private static final long MAC_AGE_TICKS = 6_000; // Five minutes at 20 ticks per second.
  private static final String KEY_ACCESS_MODE = "AccessMode";
  private static final String KEY_PASSWORD_SALT = "PasswordSalt";
  private static final String KEY_PASSWORD_HASH = "PasswordHash";
  private static final String KEY_PUBLIC_ACCESS = "PublicAccess"; // legacy owner/public migration
  private static final String KEY_VERSION = "v"; // NBT schema version; absent (0) = pre-versioning
  private static final int SCHEMA_VERSION = 1;
  private final long[] rxFrames = new long[Direction.values().length];
  private final long[] txFrames = new long[Direction.values().length];
  private final Map<String, MacEntry> macTable =
      new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MacEntry> eldest) {
          return size() > MAX_MAC_ENTRIES;
        }
      };
  private String accessMode = AccessControlledBlockEntity.MODE_UNCONFIGURED;
  private String passwordSalt = "";
  private String passwordHash = "";
  private final Set<java.util.UUID> authorizedPlayers = new HashSet<>();

  public SwitchBlockEntity(BlockPos pos, BlockState state) {
    super(ModContent.SWITCH_BLOCK_ENTITY, pos, state);
  }

  @Override
  public String accessMode() {
    return accessMode;
  }

  @Override
  public String passwordSalt() {
    return passwordSalt;
  }

  @Override
  public String passwordHash() {
    return passwordHash;
  }

  @Override
  public boolean isAuthorized(java.util.UUID player) {
    return authorizedPlayers.contains(player);
  }

  @Override
  public void authorize(java.util.UUID player) {
    authorizedPlayers.add(player);
  }

  @Override
  public void clearAuthorizations() {
    authorizedPlayers.clear();
  }

  @Override
  public void setAccessState(String mode, String salt, String hash) {
    this.accessMode = mode;
    this.passwordSalt = salt;
    this.passwordHash = hash;
    setChanged();
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
    rxFrames[ingress.ordinal()]++;
    pruneMacTable(now);
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
        send(serverLevel, egress, forwarded);
      }
      return;
    }

    for (Direction direction : Direction.values()) {
      if (direction != ingress) {
        send(serverLevel, direction, forwarded);
      }
    }
  }

  private void send(ServerLevel serverLevel, Direction egress, NetworkFrame frame) {
    if (CableNetwork.send(serverLevel, worldPosition, egress, frame) == SendResult.DELIVERED) {
      txFrames[egress.ordinal()]++;
    }
  }

  private void pruneMacTable(long now) {
    macTable.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick() > MAC_AGE_TICKS);
  }

  public String statusText() {
    long now = level instanceof ServerLevel serverLevel ? serverLevel.getGameTime() : 0L;
    pruneMacTable(now);
    StringBuilder text = new StringBuilder("Managed switch @ ").append(worldPosition).append('\n');
    text.append("Ports\n");
    for (Direction direction : Direction.values()) {
      text.append("- ")
          .append(portName(direction))
          .append(": ")
          .append(linked(direction) ? "link up" : "link down")
          .append(" rx=")
          .append(rxFrames[direction.ordinal()])
          .append(" tx=")
          .append(txFrames[direction.ordinal()])
          .append('\n');
    }
    text.append('\n').append("Learned MACs");
    if (macTable.isEmpty()) {
      text.append("\n- (none)");
      return text.toString();
    }
    for (Map.Entry<String, MacEntry> entry : macTable.entrySet()) {
      long remainingTicks = Math.max(0, MAC_AGE_TICKS - (now - entry.getValue().lastSeenTick()));
      text.append("\n- ")
          .append(entry.getKey())
          .append(" -> ")
          .append(portName(entry.getValue().port()))
          .append(" age ")
          .append(remainingTicks / 20)
          .append("s remaining");
    }
    return text.toString();
  }

  private boolean linked(Direction direction) {
    return level != null
        && level.hasChunkAt(worldPosition.relative(direction))
        && level.getBlockState(worldPosition.relative(direction)).is(ModContent.CABLE_BLOCK);
  }

  private static String portName(Direction direction) {
    return direction.name().toLowerCase(Locale.ROOT);
  }

  @Override
  protected void loadAdditional(ValueInput in) {
    super.loadAdditional(in);
    // KEY_VERSION (absent == 0, legacy) anchors future format migrations; nothing to migrate today.
    this.accessMode = in.getStringOr(KEY_ACCESS_MODE, "");
    this.passwordSalt = in.getStringOr(KEY_PASSWORD_SALT, "");
    this.passwordHash = in.getStringOr(KEY_PASSWORD_HASH, "");
    if (accessMode.isBlank()) {
      this.accessMode =
          in.getBooleanOr(KEY_PUBLIC_ACCESS, false)
              ? AccessControlledBlockEntity.MODE_PUBLIC
              : AccessControlledBlockEntity.MODE_UNCONFIGURED;
    }
  }

  @Override
  protected void saveAdditional(ValueOutput out) {
    super.saveAdditional(out);
    out.putInt(KEY_VERSION, SCHEMA_VERSION);
    out.putString(KEY_ACCESS_MODE, accessMode);
    out.putString(KEY_PASSWORD_SALT, passwordSalt);
    out.putString(KEY_PASSWORD_HASH, passwordHash);
  }

  private record MacEntry(Direction port, long lastSeenTick) {}
}
