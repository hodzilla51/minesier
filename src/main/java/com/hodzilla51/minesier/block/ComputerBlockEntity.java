package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.js.MonitorApi;
import com.hodzilla51.minesier.js.NetworkApi;
import com.hodzilla51.minesier.js.RedstoneApi;
import com.hodzilla51.minesier.net.CableNetwork;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkListener;
import com.hodzilla51.minesier.net.NetworkManager;
import com.hodzilla51.minesier.net.SendResult;
import com.hodzilla51.minesier.net.WirelessNetwork;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Backing state + VM for a placed Computer block.
 *
 * <p>Holds a scrollback transcript (input echoes + results) which is persisted to NBT. The {@link
 * JsComputer} (live scope) is transient, rebuilt on load — variables defined in a previous session
 * are intentionally not restored yet.
 */
public class ComputerBlockEntity extends BlockEntity
    implements ProgramStore, AccessControlledBlockEntity {
  private static final String KEY_TRANSCRIPT = "Transcript";
  private static final String KEY_DISK = "Disk";
  private static final String KEY_DEVICE_ID = "DeviceId";
  private static final String KEY_ADDRESS = "NetworkAddress";
  private static final String KEY_REDSTONE_OUT = "RedstoneOut";
  private static final String KEY_RESIDENT = "ResidentSource";
  private static final String KEY_ACCESS_MODE = "AccessMode";
  private static final String KEY_PASSWORD_SALT = "PasswordSalt";
  private static final String KEY_PASSWORD_HASH = "PasswordHash";
  private static final String KEY_PUBLIC_ACCESS = "PublicAccess"; // legacy owner/public migration
  private static final String WELCOME =
      String.join(
          "\n",
          "MineSIer JS terminal — try print(\"hello\")",
          "Disk files: Save /startup.js to boot a program.",
          "Network: net.send(addr, data), net.receive(), net.nic(\"north\").address()");

  /** This computer's own sandboxed JS VM (1 block = 1 VM). */
  private final JsComputer computer = new JsComputer();

  private final List<String> transcript = new ArrayList<>(List.of(WELCOME));
  private final EnumMap<Direction, NicState> nics = new EnumMap<>(Direction.class);

  /**
   * Analog redstone level (0..15) this computer emits on each face, indexed by direction ordinal.
   */
  private final int[] redstoneOutputs = new int[Direction.values().length];

  private ItemStack disk = ItemStack.EMPTY;
  private String deviceId = "";
  private String networkAddress = formatAddress(UUID.randomUUID());
  private String accessMode = AccessControlledBlockEntity.MODE_UNCONFIGURED;
  private String passwordSalt = "";
  private String passwordHash = "";
  private final Set<UUID> authorizedPlayers = new HashSet<>();

  /**
   * The program that established the current resident daemon, persisted so the daemon survives a
   * world reload. Empty when nothing is running. {@code pendingRestart} marks that it must be
   * re-run on the next server tick after loading (JS closures can't be serialized, so we re-run the
   * source).
   */
  private String residentSource = "";

  private boolean pendingRestart;

  public ComputerBlockEntity(BlockPos pos, BlockState state) {
    super(ModContent.COMPUTER_BLOCK_ENTITY, pos, state);
    for (Direction direction : Direction.values()) {
      nics.put(direction, new NicState());
    }
    computer.setNetwork(new ComputerNetworkApi());
    computer.setRedstone(new ComputerRedstoneApi());
    computer.setMonitor(new ComputerMonitorApi());
    computer.setModuleLoader(this::loadProgram);
    computer.setFileSystem(new DiskFileSystemApi());
  }

  /** The analog level this computer emits toward {@code face} (read by the block as a signal). */
  public int getRedstoneOutput(Direction face) {
    return redstoneOutputs[face.ordinal()];
  }

  public String getNetworkAddress() {
    return networkAddress;
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
  public boolean isAuthorized(UUID player) {
    return authorizedPlayers.contains(player);
  }

  @Override
  public void authorize(UUID player) {
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

  /** Called by the physical cable medium for the NIC attached to {@code face}. */
  public void offerFrame(Direction face, NetworkFrame frame) {
    NicState nic = nics.get(face);
    boolean addressedToMe =
        addressFor(face).equals(frame.destination())
            || NetworkFrame.BROADCAST.equals(frame.destination());
    if (nic == null || (!nic.promiscuous && !addressedToMe)) {
      return;
    }
    if (nic.listener != null) {
      NetworkListener listener = nic.listener;
      NetworkManager.schedule(
          queuedFrame -> {
            if (nic.listener == listener) {
              listener.onFrame(queuedFrame);
            }
          },
          frame);
      return;
    }
    if (nic.inbox.size() >= MineSIerConfig.maxInboxFrames) {
      return;
    }
    nic.inbox.addLast(frame);
    setChanged();
  }

  private NetworkFrame receiveFrame(Direction face) {
    NicState nic = nics.get(face);
    return nic == null ? null : nic.inbox.pollFirst();
  }

  /**
   * Emits a frame on a face: wireless if a modem is attached there, otherwise the cable segment.
   */
  private SendResult emit(ServerLevel serverLevel, Direction face, NetworkFrame frame) {
    BlockPos adjacent = worldPosition.relative(face);
    if (serverLevel.getBlockState(adjacent).is(ModContent.WIRELESS_MODEM_BLOCK)) {
      WirelessNetwork.deliver(serverLevel, adjacent, frame);
      return SendResult.DELIVERED;
    }
    return CableNetwork.send(serverLevel, worldPosition, face, frame);
  }

  private Direction legacyFace() {
    return getBlockState().getValue(ComputerBlock.FACING).getOpposite();
  }

  private String addressFor(Direction face) {
    if (face == legacyFace()) {
      return networkAddress;
    }
    return formatAddress(
        UUID.nameUUIDFromBytes(
            (networkAddress + "/" + face.getSerializedName())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  /** Resolves a player-facing NIC name against this computer's screen direction. */
  private Direction parseFace(String name) {
    Direction front = getBlockState().getValue(ComputerBlock.FACING);
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

  /**
   * Runs one program in this computer's VM, appending the echoed input + output to the transcript.
   */
  public void runCommand(String command) {
    computer.clearReceiveHandlers();
    String[] inputLines = command.split("\n", -1);
    transcript.add("> " + inputLines[0]);
    for (int i = 1; i < inputLines.length; i++) {
      transcript.add("  " + inputLines[i]); // continuation lines, indented
    }
    transcript.addAll(computer.run(command));
    markResident(command);
    trim();
    setChanged();
  }

  /**
   * Runs the disk's {@code startup.js} program when a disk is inserted, so a computer can boot a
   * daemon just by slotting a prepared disk. Falls back to legacy {@code startup}.
   */
  public void bootStartup() {
    // Check C: (local) first, then D: (disk), then legacy no-extension name.
    String source = loadProgram("C:/startup.js");
    if (source == null) source = loadProgram("D:/startup.js");
    if (source == null) source = loadProgram("startup");
    if (source != null && !source.isBlank()) {
      runResident(source, "[startup]");
    }
  }

  /** Re-runs {@code source} to rebuild a resident daemon (on load, or as a disk startup). */
  private void runResident(String source, String note) {
    computer.clearReceiveHandlers();
    transcript.add(note);
    transcript.addAll(computer.run(source));
    markResident(source);
    trim();
    setChanged();
  }

  /**
   * Records (or clears) the program that owns the live daemon, with a one-line hint when running.
   */
  private void markResident(String source) {
    if (computer.hasTimers()) {
      residentSource = source;
      transcript.add("[running in background — clearTimers() to stop]");
    } else {
      residentSource = "";
    }
  }

  /**
   * Server tick (one per loaded block entity). Restarts a persisted daemon after a reload, then
   * fires any due timers and folds their output into the transcript.
   */
  public void serverTick() {
    if (pendingRestart) {
      pendingRestart = false;
      if (!residentSource.isEmpty()) {
        runResident(residentSource, "[resident program restarted]");
      }
    }
    if (computer.hasTimers()) {
      List<String> out = computer.tickTimers();
      if (!out.isEmpty()) {
        transcript.addAll(out);
        trim();
        setChanged();
      }
    }
  }

  /** The full scrollback joined with newlines (for sending to the client). */
  @Override
  public String getTranscript() {
    return String.join("\n", transcript);
  }

  /** True while this computer has a live resident process. */
  public boolean isRunning() {
    return computer.hasTimers();
  }

  /**
   * A short human-readable name for the current process (first non-blank line of its source, capped
   * at 40 chars), or an empty string when nothing is running.
   */
  public String getProcessName() {
    if (!computer.hasTimers() || residentSource.isEmpty()) return "";
    String line = residentSource.stripLeading();
    int nl = line.indexOf('\n');
    String first = nl >= 0 ? line.substring(0, nl).strip() : line.strip();
    return first.length() > 40 ? first.substring(0, 40) + "…" : first;
  }

  /**
   * Kills the resident process (clears all timers and receive callbacks). The transcript gets a
   * "[stopped]" note and the block is marked dirty.
   */
  public void stopResident() {
    if (!computer.hasTimers()) return;
    computer.clearReceiveHandlers();
    residentSource = "";
    transcript.add("[stopped]");
    trim();
    setChanged();
  }

  @Override
  public ItemStack getDisk() {
    return disk;
  }

  public void setDisk(ItemStack disk) {
    this.disk = disk;
    setChanged();
  }

  @Override
  public void markChanged() {
    setChanged();
  }

  @Override
  public String ensureDeviceId() {
    if (deviceId.isEmpty()) {
      deviceId = UUID.randomUUID().toString();
      setChanged();
    }
    return deviceId;
  }

  /** Player-registered (JS) drive mounts; session-scoped (lost on reload). */
  private final java.util.Map<String, com.hodzilla51.minesier.disk.FileSystemProvider>
      dynamicMounts = new java.util.LinkedHashMap<>();

  @Override
  public java.util.Map<String, com.hodzilla51.minesier.disk.FileSystemProvider> dynamicMounts() {
    return dynamicMounts;
  }

  @Override
  public Path worldDirectory() {
    if (!(level instanceof ServerLevel sl)) return null;
    return sl.getServer().getWorldPath(LevelResource.ROOT);
  }

  private void trim() {
    while (transcript.size() > MineSIerConfig.maxTranscriptLines) {
      transcript.remove(0);
    }
  }

  @Override
  protected void loadAdditional(ValueInput in) {
    super.loadAdditional(in);
    String saved = in.getStringOr(KEY_TRANSCRIPT, WELCOME);
    transcript.clear();
    for (String line : saved.split("\n", -1)) {
      transcript.add(line);
    }
    this.disk = in.read(KEY_DISK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    this.deviceId = in.getStringOr(KEY_DEVICE_ID, "");
    this.networkAddress = in.getStringOr(KEY_ADDRESS, networkAddress);
    in.getIntArray(KEY_REDSTONE_OUT)
        .ifPresent(
            outputs -> {
              for (int i = 0; i < redstoneOutputs.length && i < outputs.length; i++) {
                redstoneOutputs[i] = Math.max(0, Math.min(15, outputs[i]));
              }
            });
    this.residentSource = in.getStringOr(KEY_RESIDENT, "");
    // A daemon was running when saved; re-run its source on the first tick after loading.
    this.pendingRestart = !residentSource.isEmpty();
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
    out.putString(KEY_TRANSCRIPT, getTranscript());
    if (!disk.isEmpty()) {
      out.store(KEY_DISK, ItemStack.CODEC, disk);
    }
    if (!deviceId.isEmpty()) {
      out.putString(KEY_DEVICE_ID, deviceId);
    }
    out.putString(KEY_ADDRESS, networkAddress);
    out.putIntArray(KEY_REDSTONE_OUT, redstoneOutputs.clone());
    if (!residentSource.isEmpty()) {
      out.putString(KEY_RESIDENT, residentSource);
    }
    out.putString(KEY_ACCESS_MODE, accessMode);
    out.putString(KEY_PASSWORD_SALT, passwordSalt);
    out.putString(KEY_PASSWORD_HASH, passwordHash);
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
        octet = (octet & 0xfe) | 0x02; // locally administered, unicast
      }
      result.append(String.format("%02x", octet));
    }
    return result.toString();
  }

  private final class ComputerNetworkApi implements NetworkApi {
    @Override
    public String address() {
      return addressFor(legacyFace());
    }

    @Override
    public SendResult send(String destination, String data) {
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
    public SendResult send(String interfaceName, String destination, String data) {
      Direction face = parseFace(interfaceName);
      return face == null ? SendResult.REJECTED : send(face, destination, data);
    }

    private SendResult send(Direction face, String destination, String data) {
      if (!(level instanceof ServerLevel serverLevel)
          || destination.isBlank()
          || data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
              > MineSIerConfig.maxFrameBytes) {
        return SendResult.REJECTED;
      }
      return emit(serverLevel, face, new NetworkFrame(addressFor(face), destination, data));
    }

    @Override
    public NetworkFrame receive(String interfaceName) {
      Direction face = parseFace(interfaceName);
      return face == null ? null : receiveFrame(face);
    }

    @Override
    public SendResult forward(String interfaceName, NetworkFrame frame) {
      Direction face = parseFace(interfaceName);
      if (!(level instanceof ServerLevel serverLevel)
          || face == null
          || frame.destination().isBlank()
          || frame.data().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
              > MineSIerConfig.maxFrameBytes) {
        return SendResult.REJECTED;
      }
      // Advance one hop; drop at the limit so a player-built switch can't loop forever either.
      NetworkFrame forwarded = frame.nextHop();
      if (forwarded == null) {
        return SendResult.REJECTED;
      }
      return emit(serverLevel, face, forwarded);
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
      transcript.addAll(lines);
      trim();
      setChanged();
    }
  }

  /** Reads the analog redstone level entering this computer from {@code face}. */
  private int readRedstoneInput(Direction face) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return 0;
    }
    BlockPos neighbor = worldPosition.relative(face);
    int power = serverLevel.getSignal(neighbor, face);
    BlockState neighborState = serverLevel.getBlockState(neighbor);
    // Redstone dust only "points" power in its connected directions; treat any adjacent dust as
    // input so a computer reads a wire running past it (matches ComputerCraft's behaviour).
    if (neighborState.is(Blocks.REDSTONE_WIRE)) {
      power = Math.max(power, neighborState.getValue(RedStoneWireBlock.POWER));
    }
    return power;
  }

  /**
   * Sets the emitted level on {@code face}, clamping to 0..15 and notifying neighbors on change.
   */
  private void setRedstoneOutput(Direction face, int requested) {
    int clamped = Math.max(0, Math.min(15, requested));
    if (redstoneOutputs[face.ordinal()] == clamped) {
      return;
    }
    redstoneOutputs[face.ordinal()] = clamped;
    setChanged();
    if (level instanceof ServerLevel serverLevel) {
      serverLevel.updateNeighborsAt(worldPosition, getBlockState().getBlock(), null);
    }
  }

  private final class ComputerRedstoneApi implements RedstoneApi {
    @Override
    public int getInput(String side) {
      Direction face = parseFace(side);
      return face == null ? -1 : readRedstoneInput(face);
    }

    @Override
    public int getOutput(String side) {
      Direction face = parseFace(side);
      return face == null ? -1 : redstoneOutputs[face.ordinal()];
    }

    @Override
    public boolean setOutput(String side, int level) {
      Direction face = parseFace(side);
      if (face == null) {
        return false;
      }
      setRedstoneOutput(face, level);
      return true;
    }

    @Override
    public String[] sides() {
      return new String[] {"front", "back", "left", "right", "up", "down"};
    }
  }

  private final class DiskFileSystemApi implements com.hodzilla51.minesier.js.FileSystemApi {
    @Override
    public java.util.List<String> list(String path) {
      return listFiles(path);
    }

    @Override
    public String read(String path) {
      return readFile(path);
    }

    @Override
    public boolean write(String path, String text) {
      return saveFile(path, text);
    }

    @Override
    public boolean remove(String path) {
      return deleteFile(path);
    }

    @Override
    public boolean exists(String path) {
      return fileExists(path);
    }

    @Override
    public boolean mount(String drive, com.hodzilla51.minesier.disk.FileSystemProvider provider) {
      String letter = ProgramStore.canonicalDrive(drive);
      if (letter == null || provider == null) return false;
      dynamicMounts().put(letter, provider);
      return true;
    }

    @Override
    public boolean unmount(String drive) {
      String letter = ProgramStore.canonicalDrive(drive);
      return letter != null && dynamicMounts().remove(letter) != null;
    }

    @Override
    public java.util.List<String> mounts() {
      return new java.util.ArrayList<>(ComputerBlockEntity.this.mounts().keySet());
    }
  }

  /** Resolves the Monitor block on {@code side}, or null if that face has none. */
  private MonitorBlockEntity monitorOn(String side) {
    Direction face = parseFace(side);
    if (face == null || !(level instanceof ServerLevel serverLevel)) {
      return null;
    }
    return serverLevel.getBlockEntity(worldPosition.relative(face))
            instanceof MonitorBlockEntity monitor
        ? monitor
        : null;
  }

  private final class ComputerMonitorApi implements MonitorApi {
    @Override
    public boolean exists(String side) {
      return monitorOn(side) != null;
    }

    @Override
    public boolean write(String side, String text) {
      MonitorBlockEntity monitor = monitorOn(side);
      if (monitor == null) {
        return false;
      }
      monitor.write(text);
      return true;
    }

    @Override
    public boolean setLine(String side, int row, String text) {
      MonitorBlockEntity monitor = monitorOn(side);
      return monitor != null && monitor.setLine(row, text);
    }

    @Override
    public boolean setText(String side, String text) {
      MonitorBlockEntity monitor = monitorOn(side);
      if (monitor == null) {
        return false;
      }
      monitor.setText(text);
      return true;
    }

    @Override
    public boolean clear(String side) {
      MonitorBlockEntity monitor = monitorOn(side);
      if (monitor == null) {
        return false;
      }
      monitor.clear();
      return true;
    }

    @Override
    public int rows(String side) {
      return monitorOn(side) == null ? -1 : MonitorBlockEntity.ROWS;
    }

    @Override
    public int columns(String side) {
      return monitorOn(side) == null ? -1 : MonitorBlockEntity.COLUMNS;
    }
  }

  private static final class NicState {
    // Concurrent so a future off-thread producer (e.g. networked turtles, whose VM
    // runs on a worker thread) can't corrupt the inbox vs. the server-thread reader.
    final Deque<NetworkFrame> inbox = new java.util.concurrent.ConcurrentLinkedDeque<>();
    volatile boolean promiscuous;
    volatile NetworkListener listener;
  }

  @Override
  public void setRemoved() {
    computer.clearReceiveHandlers();
    super.setRemoved();
  }
}
