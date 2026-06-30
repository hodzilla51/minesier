package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.turtle.TurtleNetworkState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Persistent shell + state for a placed turtle: its VM (so variables survive), scrollback
 * transcript, fuel, inventory, and inserted disk. Facing lives in the block's state (auto-synced to
 * clients). While a program runs, the authoritative live state lives in the {@code
 * turtle.TurtleManager}'s brain (the block hops and its block entity is recreated); the brain
 * writes final state back here via {@link #applyResult}.
 */
public class TurtleBlockEntity extends BlockEntity
    implements ProgramStore, AccessControlledBlockEntity {
  private static final String KEY_TRANSCRIPT = "Transcript";
  private static final String KEY_DEVICE_ID = "DeviceId";
  private static final String KEY_FUEL = "Fuel";
  private static final String KEY_SELECTED = "SelectedSlot";
  private static final String KEY_DISK = "Disk";
  private static final String KEY_FOOT = "FootEquipment";
  private static final String KEY_ARM = "ArmEquipment";
  private static final String KEY_TOP = "TopEquipment";
  private static final String KEY_ADDRESS = "NetworkAddress";
  private static final String KEY_ACCESS_MODE = "AccessMode";
  private static final String KEY_PASSWORD_SALT = "PasswordSalt";
  private static final String KEY_PASSWORD_HASH = "PasswordHash";
  private static final String KEY_PUBLIC_ACCESS = "PublicAccess"; // legacy owner/public migration
  private static final String KEY_VERSION = "v"; // NBT schema version; absent (0) = pre-versioning
  private static final int SCHEMA_VERSION = 1;
  private static final int INVENTORY_SIZE = 16;
  public static final int EQUIPMENT_SIZE = 3;
  public static final int EQUIPMENT_FOOT = 0;
  public static final int EQUIPMENT_ARM = 1;
  public static final int EQUIPMENT_TOP = 2;
  private static final int DEFAULT_FUEL = 1000;
  private static final String WELCOME =
      String.join(
          "\n",
          "MineSIer turtle — try turtle.forward()",
          "Use Inventory for 16 slots, Equipment for foot/arm/top parts.",
          "Disk files work here too: fs.read(path), fs.write(path, text).");

  private JsComputer vm = new JsComputer();
  private String deviceId = "";
  private final List<String> transcript = new ArrayList<>(List.of(WELCOME));
  private int fuel = DEFAULT_FUEL;
  private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
  private int selectedSlot = 0;
  private ItemStack disk = ItemStack.EMPTY;
  private NonNullList<ItemStack> equipment = NonNullList.withSize(EQUIPMENT_SIZE, ItemStack.EMPTY);
  private TurtleNetworkState network = new TurtleNetworkState();
  private String accessMode = AccessControlledBlockEntity.MODE_UNCONFIGURED;
  private String passwordSalt = "";
  private String passwordHash = "";
  private final Set<java.util.UUID> authorizedPlayers = new HashSet<>();

  public TurtleBlockEntity(BlockPos pos, BlockState state) {
    super(ModContent.TURTLE_BLOCK_ENTITY, pos, state);
  }

  public int getFuel() {
    return fuel;
  }

  public JsComputer getVm() {
    return vm;
  }

  public NonNullList<ItemStack> getInventory() {
    return inventory;
  }

  public NonNullList<ItemStack> getEquipment() {
    return equipment;
  }

  public void setEquipment(NonNullList<ItemStack> equipment) {
    this.equipment = copyEquipment(equipment);
    equipmentChanged();
  }

  public void equipmentChanged() {
    setChanged();
    if (level != null && !level.isClientSide()) {
      BlockState state = getBlockState();
      level.sendBlockUpdated(worldPosition, state, state, 3);
    }
  }

  public int getSelectedSlot() {
    return selectedSlot;
  }

  public TurtleNetworkState getNetwork() {
    return network;
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

  /** Receives a frame from the physical cable segment on the specified turtle face. */
  public void offerFrame(Direction face, NetworkFrame frame) {
    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      network.attach(serverLevel, worldPosition, getBlockState().getValue(TurtleBlock.FACING));
    }
    network.offerFrame(face, frame);
  }

  /** Carries live NIC state into the replacement block entity after a turtle block-hop. */
  public void adoptNetwork(TurtleNetworkState network) {
    this.network = network;
    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
      network.attach(serverLevel, worldPosition, getBlockState().getValue(TurtleBlock.FACING));
    }
  }

  /** Appends output produced by a network receive callback after the foreground program ended. */
  public void appendNetworkOutput(List<String> lines) {
    transcript.addAll(lines);
    while (transcript.size() > MineSIerConfig.maxTranscriptLines) {
      transcript.remove(0);
    }
    setChanged();
  }

  @Override
  public String getTranscript() {
    return String.join("\n", transcript);
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

  /**
   * Per-tick flush of dup-critical live state from a running program onto the turtle's
   * current-position block entity. Without this, a world autosave (or a crash) mid-program saves
   * stale BE state while the turtle's physical moves/blocks are already persisted — letting fuel and
   * items duplicate on reload. Transcript is intentionally left to {@link #applyResult}/{@link
   * #appendNetworkOutput} (its mid-run loss is cosmetic, not a dupe).
   */
  public void syncLiveState(
      int fuel, NonNullList<ItemStack> inventory, NonNullList<ItemStack> equipment, int selectedSlot) {
    this.fuel = fuel;
    this.inventory = copyInventory(inventory);
    this.equipment = copyEquipment(equipment);
    this.selectedSlot = selectedSlot;
    setChanged();
  }

  /** Lands a finished program's state onto this (the turtle's final-position) block entity. */
  public void applyResult(
      JsComputer vm,
      int fuel,
      NonNullList<ItemStack> inventory,
      NonNullList<ItemStack> equipment,
      int selectedSlot,
      ItemStack disk,
      String transcript,
      String accessMode,
      String passwordSalt,
      String passwordHash) {
    this.vm = vm;
    this.fuel = fuel;
    this.inventory = inventory;
    this.equipment = copyEquipment(equipment);
    this.selectedSlot = selectedSlot;
    this.disk = disk;
    this.transcript.clear();
    for (String line : transcript.split("\n", -1)) {
      this.transcript.add(line);
    }
    this.accessMode = accessMode;
    this.passwordSalt = passwordSalt;
    this.passwordHash = passwordHash;
    setChanged();
  }

  @Override
  protected void loadAdditional(ValueInput in) {
    super.loadAdditional(in);
    // KEY_VERSION (absent == 0, legacy) anchors future format migrations; all fields below default
    // gracefully so there is nothing to migrate today.
    String saved = in.getStringOr(KEY_TRANSCRIPT, WELCOME);
    transcript.clear();
    for (String line : saved.split("\n", -1)) {
      transcript.add(line);
    }
    this.fuel = in.getIntOr(KEY_FUEL, DEFAULT_FUEL);
    this.selectedSlot = in.getIntOr(KEY_SELECTED, 0);
    this.inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(in, this.inventory);
    this.disk = in.read(KEY_DISK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    this.deviceId = in.getStringOr(KEY_DEVICE_ID, "");
    this.equipment = NonNullList.withSize(EQUIPMENT_SIZE, ItemStack.EMPTY);
    this.equipment.set(EQUIPMENT_FOOT, in.read(KEY_FOOT, ItemStack.CODEC).orElse(ItemStack.EMPTY));
    this.equipment.set(EQUIPMENT_ARM, in.read(KEY_ARM, ItemStack.CODEC).orElse(ItemStack.EMPTY));
    this.equipment.set(EQUIPMENT_TOP, in.read(KEY_TOP, ItemStack.CODEC).orElse(ItemStack.EMPTY));
    this.network.setNetworkAddress(in.getStringOr(KEY_ADDRESS, network.getNetworkAddress()));
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
    out.putString(KEY_TRANSCRIPT, getTranscript());
    out.putInt(KEY_FUEL, fuel);
    out.putInt(KEY_SELECTED, selectedSlot);
    ContainerHelper.saveAllItems(out, inventory);
    if (!disk.isEmpty()) {
      out.store(KEY_DISK, ItemStack.CODEC, disk);
    }
    if (!deviceId.isEmpty()) {
      out.putString(KEY_DEVICE_ID, deviceId);
    }
    storeEquipment(out, KEY_FOOT, equipment.get(EQUIPMENT_FOOT));
    storeEquipment(out, KEY_ARM, equipment.get(EQUIPMENT_ARM));
    storeEquipment(out, KEY_TOP, equipment.get(EQUIPMENT_TOP));
    out.putString(KEY_ADDRESS, network.getNetworkAddress());
    out.putString(KEY_ACCESS_MODE, accessMode);
    out.putString(KEY_PASSWORD_SALT, passwordSalt);
    out.putString(KEY_PASSWORD_HASH, passwordHash);
  }

  private static void storeEquipment(ValueOutput out, String key, ItemStack stack) {
    if (!stack.isEmpty()) {
      out.store(key, ItemStack.CODEC, stack);
    }
  }

  private static NonNullList<ItemStack> copyEquipment(NonNullList<ItemStack> source) {
    NonNullList<ItemStack> copy = NonNullList.withSize(EQUIPMENT_SIZE, ItemStack.EMPTY);
    for (int i = 0; i < Math.min(EQUIPMENT_SIZE, source.size()); i++) {
      copy.set(i, source.get(i).copy());
    }
    return copy;
  }

  private static NonNullList<ItemStack> copyInventory(NonNullList<ItemStack> source) {
    NonNullList<ItemStack> copy = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    for (int i = 0; i < Math.min(INVENTORY_SIZE, source.size()); i++) {
      copy.set(i, source.get(i).copy());
    }
    return copy;
  }

  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return saveWithoutMetadata(registries);
  }
}
