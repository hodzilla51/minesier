package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.TurtleApi;
import com.hodzilla51.minesier.net.TurtleMoveS2C;
import com.hodzilla51.minesier.net.TurtleTurnS2C;
import com.hodzilla51.minesier.net.TurtleVisualAction;
import com.hodzilla51.minesier.net.TurtleVisualS2C;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative implementation of {@link TurtleApi}: moving is the CC-style "block hop" —
 * place the turtle block in the target cell and clear the old one. Tracks its own mutable {@code
 * pos}/{@code facing}/{@code fuel} so a program can chain actions within one run even as the
 * underlying block entity is replaced.
 */
public class TurtleAccess implements TurtleApi {
  private final Level level;
  private BlockPos pos;
  private Direction facing;
  private int fuel;
  private final NonNullList<ItemStack> inventory;
  private int selectedSlot;

  public TurtleAccess(
      Level level,
      BlockPos pos,
      Direction facing,
      int fuel,
      NonNullList<ItemStack> inventory,
      int selectedSlot) {
    this.level = level;
    this.pos = pos;
    this.facing = facing;
    this.fuel = fuel;
    this.inventory = inventory;
    this.selectedSlot = selectedSlot;
  }

  public BlockPos pos() {
    return pos;
  }

  public Direction facing() {
    return facing;
  }

  public int fuel() {
    return fuel;
  }

  public NonNullList<ItemStack> inventory() {
    return inventory;
  }

  public int selectedSlot() {
    return selectedSlot;
  }

  @Override
  public boolean forward() {
    return move(facing);
  }

  @Override
  public boolean back() {
    return move(facing.getOpposite());
  }

  @Override
  public boolean turnLeft() {
    facing = facing.getCounterClockWise();
    applyFacing(false);
    return true;
  }

  @Override
  public boolean turnRight() {
    facing = facing.getClockWise();
    applyFacing(true);
    return true;
  }

  /** Pushes the current facing into the block's state (syncs to clients) and animates the turn. */
  private void applyFacing(boolean clockwise) {
    BlockState here = level.getBlockState(pos);
    if (here.is(ModContent.TURTLE_BLOCK)) {
      level.setBlock(pos, here.setValue(TurtleBlock.FACING, facing), 3);
    }
    if (level instanceof ServerLevel serverLevel) {
      for (ServerPlayer player : PlayerLookup.tracking(serverLevel, pos)) {
        ServerPlayNetworking.send(player, new TurtleTurnS2C(pos, clockwise));
      }
    }
  }

  @Override
  public boolean dig() {
    BlockPos target = pos.relative(facing);
    BlockState state = level.getBlockState(target);
    if (state.canBeReplaced()) {
      return false; // nothing solid to dig
    }
    String pickupDetail = "GET";
    // Collect the block's drops into the turtle's inventory (overflow pops into the world).
    if (level instanceof ServerLevel serverLevel) {
      List<ItemStack> drops =
          Block.getDrops(state, serverLevel, target, level.getBlockEntity(target));
      for (ItemStack drop : drops) {
        if (!drop.isEmpty()) {
          pickupDetail = BuiltInRegistries.ITEM.getKey(drop.getItem()).getPath().toUpperCase();
        }
        ItemStack leftover = insert(drop);
        if (!leftover.isEmpty()) {
          Block.popResource(level, pos, leftover);
        }
      }
    }
    boolean destroyed =
        level.destroyBlock(target, false, null, 512); // false: we already took drops
    if (destroyed) {
      emitVisual(TurtleVisualAction.DIG, "DIG");
      emitVisual(TurtleVisualAction.PICKUP, pickupDetail);
    }
    return destroyed;
  }

  @Override
  public boolean place(String blockId) {
    Identifier id = Identifier.tryParse(blockId);
    if (id == null) {
      return false;
    }
    Block block = BuiltInRegistries.BLOCK.getValue(id);
    if (!BuiltInRegistries.BLOCK.getKey(block).equals(id)) {
      return false; // unknown block id (defaulted to air)
    }
    BlockPos target = pos.relative(facing);
    if (!level.getBlockState(target).canBeReplaced()) {
      return false; // occupied
    }
    boolean placed = level.setBlock(target, block.defaultBlockState(), 3);
    if (placed) {
      emitVisual(TurtleVisualAction.PLACE, "PUT");
    }
    return placed;
  }

  @Override
  public boolean placeSelected() {
    ItemStack stack = inventory.get(selectedSlot);
    if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
      return false;
    }
    BlockPos target = pos.relative(facing);
    if (!level.getBlockState(target).canBeReplaced()) {
      return false; // occupied
    }
    if (level.setBlock(target, blockItem.getBlock().defaultBlockState(), 3)) {
      stack.shrink(1);
      emitVisual(TurtleVisualAction.PLACE, "PUT");
      return true;
    }
    return false;
  }

  @Override
  public void select(int slot) {
    this.selectedSlot = Math.max(0, Math.min(inventory.size() - 1, slot - 1));
  }

  @Override
  public int getSelectedSlot() {
    return selectedSlot + 1;
  }

  @Override
  public int getItemCount(int slot) {
    int index = slot <= 0 ? selectedSlot : Math.min(inventory.size() - 1, slot - 1);
    return inventory.get(index).getCount();
  }

  /** Merges {@code stack} into existing matching slots then empty slots; returns the leftover. */
  private ItemStack insert(ItemStack stack) {
    for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
      ItemStack slot = inventory.get(i);
      if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
        int space = slot.getMaxStackSize() - slot.getCount();
        int moved = Math.min(space, stack.getCount());
        slot.grow(moved);
        stack.shrink(moved);
      }
    }
    for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
      if (inventory.get(i).isEmpty()) {
        inventory.set(i, stack.copy());
        stack.setCount(0);
      }
    }
    return stack;
  }

  @Override
  public boolean detect() {
    return !level.getBlockState(pos.relative(facing)).canBeReplaced();
  }

  @Override
  public String inspect() {
    BlockState state = level.getBlockState(pos.relative(facing));
    if (state.isAir()) {
      return "";
    }
    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
  }

  @Override
  public int getFuelLevel() {
    return fuel;
  }

  @Override
  public void refuel(int amount) {
    if (amount > 0) {
      fuel += amount;
    }
  }

  private boolean move(Direction direction) {
    if (fuel <= 0) {
      emitVisual(TurtleVisualAction.OUT_OF_FUEL, "!");
      return false; // out of fuel
    }
    BlockPos target = pos.relative(direction);
    if (!level.getBlockState(target).canBeReplaced()) {
      return false; // blocked
    }
    level.setBlock(
        target,
        ModContent.TURTLE_BLOCK.defaultBlockState().setValue(TurtleBlock.FACING, facing),
        3);
    level.removeBlock(pos, false);
    // Tell nearby clients to slide the turtle in from where it came (smooth animation).
    if (level instanceof ServerLevel serverLevel) {
      int fromDir = direction.getOpposite().ordinal();
      for (ServerPlayer player : PlayerLookup.tracking(serverLevel, target)) {
        ServerPlayNetworking.send(player, new TurtleMoveS2C(target, fromDir));
      }
    }
    pos = target;
    fuel--;
    return true;
  }

  private void emitVisual(TurtleVisualAction action, String detail) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return;
    }
    TurtleVisualS2C payload = new TurtleVisualS2C(pos, action, detail);
    for (ServerPlayer player : PlayerLookup.tracking(serverLevel, pos)) {
      ServerPlayNetworking.send(player, payload);
    }
  }
}
