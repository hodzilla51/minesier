package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.turtle.TurtleNetworkState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Persistent shell + state for a placed turtle: its VM (so variables survive), scrollback
 * transcript, fuel, inventory, and inserted disk. Facing lives in the block's state (auto-synced to
 * clients). While a program runs, the authoritative live state lives in the {@code
 * turtle.TurtleManager}'s brain (the block hops and its block entity is recreated); the brain
 * writes final state back here via {@link #applyResult}.
 */
public class TurtleBlockEntity extends BlockEntity implements ProgramStore {
  private static final String KEY_TRANSCRIPT = "Transcript";
  private static final String KEY_FUEL = "Fuel";
  private static final String KEY_SELECTED = "SelectedSlot";
  private static final String KEY_DISK = "Disk";
  private static final String KEY_ADDRESS = "NetworkAddress";
  private static final int INVENTORY_SIZE = 16;
  private static final int DEFAULT_FUEL = 1000;
  private static final String WELCOME = "MineSIer turtle — try turtle.forward()";

  private JsComputer vm = new JsComputer();
  private final List<String> transcript = new ArrayList<>(List.of(WELCOME));
  private int fuel = DEFAULT_FUEL;
  private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
  private int selectedSlot = 0;
  private ItemStack disk = ItemStack.EMPTY;
  private TurtleNetworkState network = new TurtleNetworkState();

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

  public int getSelectedSlot() {
    return selectedSlot;
  }

  /** Selects an inventory slot from the terminal inventory panel. */
  public void selectInventorySlot(int slot) {
    selectedSlot = Math.max(0, Math.min(inventory.size() - 1, slot));
    setChanged();
  }

  public TurtleNetworkState getNetwork() {
    return network;
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
    while (transcript.size() > 200) {
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

  /** Lands a finished program's state onto this (the turtle's final-position) block entity. */
  public void applyResult(
      JsComputer vm,
      int fuel,
      NonNullList<ItemStack> inventory,
      int selectedSlot,
      ItemStack disk,
      String transcript) {
    this.vm = vm;
    this.fuel = fuel;
    this.inventory = inventory;
    this.selectedSlot = selectedSlot;
    this.disk = disk;
    this.transcript.clear();
    for (String line : transcript.split("\n", -1)) {
      this.transcript.add(line);
    }
    setChanged();
  }

  @Override
  protected void loadAdditional(ValueInput in) {
    super.loadAdditional(in);
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
    this.network.setNetworkAddress(in.getStringOr(KEY_ADDRESS, network.getNetworkAddress()));
  }

  @Override
  protected void saveAdditional(ValueOutput out) {
    super.saveAdditional(out);
    out.putString(KEY_TRANSCRIPT, getTranscript());
    out.putInt(KEY_FUEL, fuel);
    out.putInt(KEY_SELECTED, selectedSlot);
    ContainerHelper.saveAllItems(out, inventory);
    if (!disk.isEmpty()) {
      out.store(KEY_DISK, ItemStack.CODEC, disk);
    }
    out.putString(KEY_ADDRESS, network.getNetworkAddress());
  }
}
