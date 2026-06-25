package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.TurtleApi;
import com.hodzilla51.minesier.net.NetworkManager;
import com.hodzilla51.minesier.net.TurtleMoveS2C;
import com.hodzilla51.minesier.net.TurtleTurnS2C;
import com.hodzilla51.minesier.net.TurtleVisualAction;
import com.hodzilla51.minesier.net.TurtleVisualS2C;
import com.hodzilla51.minesier.turtle.TurtleNetworkState;
import java.util.ArrayList;
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
import net.minecraft.tags.BlockTags;
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
  private final NonNullList<ItemStack> equipment;
  private int selectedSlot;
  private final TurtleNetworkState network;
  private BlockPos pendingDigTarget;
  private BlockState pendingDigState;

  public TurtleAccess(
      Level level,
      BlockPos pos,
      Direction facing,
      int fuel,
      NonNullList<ItemStack> inventory,
      NonNullList<ItemStack> equipment,
      int selectedSlot,
      TurtleNetworkState network) {
    this.level = level;
    this.pos = pos;
    this.facing = facing;
    this.fuel = fuel;
    this.inventory = inventory;
    this.equipment = equipment;
    this.selectedSlot = selectedSlot;
    this.network = network;
    if (level instanceof ServerLevel serverLevel) {
      network.attach(serverLevel, pos, facing);
    }
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

  public NonNullList<ItemStack> equipment() {
    return equipment;
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
  public boolean up() {
    return move(Direction.UP);
  }

  @Override
  public boolean down() {
    return move(Direction.DOWN);
  }

  @Override
  public int actionTicks(String op, Object[] args, int defaultTicks) {
    return switch (op) {
      case "forward" -> movementCost(facing).ticks();
      case "back" -> movementCost(facing.getOpposite()).ticks();
      case "up" -> movementCost(Direction.UP).ticks();
      case "down" -> movementCost(Direction.DOWN).ticks();
      case "dig" -> beginDigTicks(defaultTicks);
      case "scan" -> hasProximitySensor() ? 20 : 0;
      default -> defaultTicks;
    };
  }

  @Override
  public void actionProgress(String op, Object[] args, int elapsedTicks, int totalTicks) {
    if (!"dig".equals(op) || totalTicks <= 0 || !(level instanceof ServerLevel serverLevel)) {
      return;
    }
    if (!digTargetStillValid()) {
      if (pendingDigTarget != null) {
        clearDigProgress(serverLevel, pendingDigTarget);
      }
      return;
    }
    int stage = Math.clamp((elapsedTicks * 10) / totalTicks, 0, 9);
    serverLevel.destroyBlockProgress(digProgressId(pendingDigTarget), pendingDigTarget, stage);
  }

  @Override
  public boolean actionStillValid(String op, Object[] args) {
    return !"dig".equals(op) || digTargetStillValid();
  }

  @Override
  public void clearActionProgress(String op, Object[] args) {
    if (!"dig".equals(op) || !(level instanceof ServerLevel serverLevel)) {
      return;
    }
    if (pendingDigTarget != null) {
      clearDigProgress(serverLevel, pendingDigTarget);
    }
    pendingDigTarget = null;
    pendingDigState = null;
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
      network.attach(serverLevel, pos, facing);
      for (ServerPlayer player : PlayerLookup.tracking(serverLevel, pos)) {
        ServerPlayNetworking.send(player, new TurtleTurnS2C(pos, clockwise));
      }
    }
  }

  @Override
  public boolean dig() {
    BlockPos target = pendingDigTarget != null ? pendingDigTarget : pos.relative(facing);
    BlockState state = level.getBlockState(target);
    if (state.canBeReplaced() || !digTargetStillValid()) {
      return false; // nothing solid to dig
    }
    String pickupDetail = "GET";
    ItemStack tool = armTool();
    // Collect the block's drops into the turtle's inventory (overflow pops into the world).
    if (level instanceof ServerLevel serverLevel) {
      List<ItemStack> drops =
          Block.getDrops(state, serverLevel, target, level.getBlockEntity(target), null, tool);
      for (ItemStack drop : drops) {
        if (!drop.isEmpty()) {
          pickupDetail = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
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
      damageArmTool(state, target);
      emitVisual(TurtleVisualAction.DIG, "DIG");
      emitVisual(TurtleVisualAction.PICKUP, pickupDetail);
    }
    return destroyed;
  }

  private ItemStack armTool() {
    return equipment.get(TurtleBlockEntity.EQUIPMENT_ARM);
  }

  private int beginDigTicks(int defaultTicks) {
    BlockPos target = pos.relative(facing);
    BlockState state = level.getBlockState(target);
    if (state.canBeReplaced()) {
      pendingDigTarget = null;
      pendingDigState = null;
      return defaultTicks;
    }
    float hardness = state.getDestroySpeed(level, target);
    if (hardness < 0f) {
      pendingDigTarget = null;
      pendingDigState = null;
      return defaultTicks;
    }
    pendingDigTarget = target;
    pendingDigState = state;
    ItemStack tool = armTool();
    float speed = Math.max(1.0f, tool.getDestroySpeed(state));
    boolean correctTool = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    float divisor = correctTool ? 30.0f : 100.0f;
    int ticks = (int) Math.ceil((hardness * divisor) / speed);
    return Math.max(1, ticks);
  }

  private boolean digTargetStillValid() {
    return pendingDigTarget != null
        && pendingDigState != null
        && level.isLoaded(pendingDigTarget)
        && level.getBlockState(pendingDigTarget).equals(pendingDigState);
  }

  private void damageArmTool(BlockState state, BlockPos target) {
    ItemStack tool = armTool();
    if (tool.isEmpty() || !tool.isDamageableItem() || state.getDestroySpeed(level, target) <= 0f) {
      return;
    }
    if (level instanceof ServerLevel serverLevel) {
      tool.hurtAndBreak(
          1,
          serverLevel,
          null,
          ignored -> equipment.set(TurtleBlockEntity.EQUIPMENT_ARM, ItemStack.EMPTY));
    }
  }

  private void clearDigProgress(ServerLevel serverLevel, BlockPos target) {
    serverLevel.destroyBlockProgress(digProgressId(target), target, -1);
  }

  private int digProgressId(BlockPos target) {
    return 0x4D510000 ^ pos.hashCode() ^ target.hashCode();
  }

  @Override
  public boolean place(String blockId) {
    Identifier id = Identifier.tryParse(blockId);
    if (id == null) {
      return false;
    }
    return placeSelectedBlock(id);
  }

  @Override
  public boolean placeSelected() {
    return placeSelectedBlock(null);
  }

  /** Places one selected block item, optionally requiring the requested block id. */
  private boolean placeSelectedBlock(Identifier expectedBlockId) {
    ItemStack stack = inventory.get(selectedSlot);
    if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
      return false;
    }
    Identifier actualBlockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
    if (expectedBlockId != null && !actualBlockId.equals(expectedBlockId)) {
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
  public void waitTicks(int ticks) {
    // TurtleBrain owns the tick-paced wait. This method intentionally has no world effect.
  }

  @Override
  public void visual(TurtleVisualAction action, String detail) {
    NetworkManager.schedule(() -> emitVisual(action, detail));
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

  @Override
  public List<ScanResult> scan() {
    if (!hasProximitySensor()) {
      return List.of();
    }
    if (fuel < 1) {
      emitVisual(TurtleVisualAction.OUT_OF_FUEL, "!");
      return List.of();
    }
    fuel--;
    List<ScanResult> results = new ArrayList<>();
    for (int dx = -3; dx <= 3; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -3; dz <= 3; dz++) {
          if (dx == 0 && dy == 0 && dz == 0) {
            continue;
          }
          BlockPos scanPos = pos.offset(dx, dy, dz);
          if (!level.isLoaded(scanPos)) {
            continue;
          }
          BlockState state = level.getBlockState(scanPos);
          if (state.isAir()) {
            continue;
          }
          String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
          results.add(new ScanResult(dx, dy, dz, blockId));
        }
      }
    }
    return results;
  }

  private boolean move(Direction direction) {
    MovementCost cost = movementCost(direction);
    if (fuel < cost.fuel()) {
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
    if (level.getBlockEntity(target) instanceof TurtleBlockEntity turtle) {
      turtle.adoptNetwork(network);
      turtle.setEquipment(equipment);
    }
    level.removeBlock(pos, false);
    // Tell nearby clients to slide the turtle in from where it came (smooth animation).
    if (level instanceof ServerLevel serverLevel) {
      int fromDir = direction.getOpposite().ordinal();
      for (ServerPlayer player : PlayerLookup.tracking(serverLevel, target)) {
        ServerPlayNetworking.send(player, new TurtleMoveS2C(target, fromDir, cost.ticks()));
      }
    }
    pos = target;
    if (level instanceof ServerLevel serverLevel) {
      network.attach(serverLevel, pos, facing);
    }
    fuel -= cost.fuel();
    return true;
  }

  private MovementCost movementCost(Direction direction) {
    FootProfile profile = footProfile();
    if (!isGrounded()) {
      return profile.airborne();
    }
    if (direction.getAxis().isVertical()) {
      return profile.verticalGrounded();
    }
    return profile.horizontal(terrainBelow());
  }

  private boolean isGrounded() {
    return !level.getBlockState(pos.below()).canBeReplaced();
  }

  private Terrain terrainBelow() {
    BlockState state = level.getBlockState(pos.below());
    if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
      return Terrain.PICKAXE;
    }
    if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
      return Terrain.SHOVEL;
    }
    if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
      return Terrain.AXE;
    }
    if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
      return Terrain.HOE;
    }
    return Terrain.OTHER;
  }

  private FootProfile footProfile() {
    ItemStack foot = equipment.get(TurtleBlockEntity.EQUIPMENT_FOOT);
    if (foot.is(ModContent.WHEEL_FOOT_PART)) {
      return FootProfile.WHEEL;
    }
    if (foot.is(ModContent.CRAWLER_FOOT_PART)) {
      return FootProfile.CRAWLER;
    }
    if (foot.is(ModContent.HOVER_FOOT_PART)) {
      return FootProfile.HOVER;
    }
    return FootProfile.NONE;
  }

  private boolean hasProximitySensor() {
    return equipment.get(TurtleBlockEntity.EQUIPMENT_TOP).is(ModContent.PROXIMITY_SENSOR_MODULE);
  }

  private enum Terrain {
    PICKAXE,
    SHOVEL,
    AXE,
    HOE,
    OTHER
  }

  private record MovementCost(int ticks, int fuel) {}

  private enum FootProfile {
    NONE {
      @Override
      MovementCost horizontal(Terrain terrain) {
        return new MovementCost(24, 1);
      }

      @Override
      MovementCost verticalGrounded() {
        return new MovementCost(32, 2);
      }

      @Override
      MovementCost airborne() {
        return new MovementCost(48, 3);
      }
    },
    WHEEL {
      @Override
      MovementCost horizontal(Terrain terrain) {
        return switch (terrain) {
          case PICKAXE -> new MovementCost(8, 1);
          case SHOVEL -> new MovementCost(16, 1);
          case AXE, HOE, OTHER -> new MovementCost(14, 1);
        };
      }

      @Override
      MovementCost verticalGrounded() {
        return new MovementCost(32, 2);
      }

      @Override
      MovementCost airborne() {
        return new MovementCost(40, 3);
      }
    },
    CRAWLER {
      @Override
      MovementCost horizontal(Terrain terrain) {
        return switch (terrain) {
          case PICKAXE -> new MovementCost(14, 1);
          case SHOVEL -> new MovementCost(11, 1);
          case AXE, HOE, OTHER -> new MovementCost(14, 1);
        };
      }

      @Override
      MovementCost verticalGrounded() {
        return new MovementCost(22, 2);
      }

      @Override
      MovementCost airborne() {
        return new MovementCost(36, 3);
      }
    },
    HOVER {
      @Override
      MovementCost horizontal(Terrain terrain) {
        return new MovementCost(12, 2);
      }

      @Override
      MovementCost verticalGrounded() {
        return new MovementCost(12, 2);
      }

      @Override
      MovementCost airborne() {
        return new MovementCost(12, 2);
      }
    };

    abstract MovementCost horizontal(Terrain terrain);

    abstract MovementCost verticalGrounded();

    abstract MovementCost airborne();
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
