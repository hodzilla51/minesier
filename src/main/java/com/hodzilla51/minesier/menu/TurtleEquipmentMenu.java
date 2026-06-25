package com.hodzilla51.minesier.menu;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.turtle.TurtleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Equipment menu for Turtle foot, arm, and top extension slots. */
public class TurtleEquipmentMenu extends AbstractContainerMenu {
  public static final int PLAYER_START = 0;
  public static final int PLAYER_END = 36;
  public static final int EQUIPMENT_START = PLAYER_END;
  public static final int FOOT_SLOT = EQUIPMENT_START + TurtleBlockEntity.EQUIPMENT_FOOT;
  public static final int ARM_SLOT = EQUIPMENT_START + TurtleBlockEntity.EQUIPMENT_ARM;
  public static final int TOP_SLOT = EQUIPMENT_START + TurtleBlockEntity.EQUIPMENT_TOP;
  public static final int EQUIPMENT_END = EQUIPMENT_START + TurtleBlockEntity.EQUIPMENT_SIZE;

  private final ContainerLevelAccess access;
  private final BlockPos pos;

  public TurtleEquipmentMenu(int id, Inventory playerInv, TurtleMenuData data) {
    this(
        id,
        playerInv,
        new SimpleContainer(TurtleBlockEntity.EQUIPMENT_SIZE),
        ContainerLevelAccess.NULL,
        data.pos(),
        data.screenWidth(),
        data.screenHeight());
  }

  public TurtleEquipmentMenu(
      int id,
      Inventory playerInv,
      Container equipment,
      ContainerLevelAccess access,
      int screenWidth,
      int screenHeight) {
    this(
        id,
        playerInv,
        equipment,
        access,
        access.evaluate((level, position) -> position, BlockPos.ZERO),
        screenWidth,
        screenHeight);
  }

  private TurtleEquipmentMenu(
      int id,
      Inventory playerInv,
      Container equipment,
      ContainerLevelAccess access,
      BlockPos pos,
      int screenWidth,
      int screenHeight) {
    super(ModContent.TURTLE_EQUIPMENT_MENU, id);
    this.access = access;
    this.pos = pos;

    int inventoryX = (Math.max(256, screenWidth) - 9 * 18) / 2;
    int inventoryY = Math.max(216, screenHeight) - 12 - 72;
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        addSlot(
            new Slot(playerInv, 9 + row * 9 + col, inventoryX + col * 18, inventoryY + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      addSlot(new Slot(playerInv, col, inventoryX + col * 18, inventoryY + 58));
    }

    int center = Math.max(256, screenWidth) / 2;
    int equipmentY = 76;
    addSlot(
        new EquipmentSlot(equipment, TurtleBlockEntity.EQUIPMENT_FOOT, center - 63, equipmentY));
    addSlot(new EquipmentSlot(equipment, TurtleBlockEntity.EQUIPMENT_ARM, center - 9, equipmentY));
    addSlot(new EquipmentSlot(equipment, TurtleBlockEntity.EQUIPMENT_TOP, center + 45, equipmentY));
  }

  public BlockPos turtlePos() {
    return pos;
  }

  public ItemStack equipmentItem(int slot) {
    return getSlot(EQUIPMENT_START + slot).getItem();
  }

  private boolean running() {
    return access.evaluate((level, p) -> TurtleManager.isRunning(level, p), false);
  }

  @Override
  public boolean stillValid(Player player) {
    return stillValid(access, player, ModContent.TURTLE_BLOCK)
        && access.evaluate((level, p) -> !TurtleManager.isRunning(level, p), true);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (running()) {
      return ItemStack.EMPTY;
    }
    Slot slot = slots.get(index);
    if (!slot.hasItem()) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    if (index < PLAYER_END) {
      if (isFootPart(stack)) {
        if (!moveItemStackTo(stack, FOOT_SLOT, FOOT_SLOT + 1, false)) {
          return ItemStack.EMPTY;
        }
      } else if (isTopModule(stack)) {
        if (!moveItemStackTo(stack, TOP_SLOT, TOP_SLOT + 1, false)) {
          return ItemStack.EMPTY;
        }
      } else if (isTool(stack)) {
        if (!moveItemStackTo(stack, ARM_SLOT, ARM_SLOT + 1, false)) {
          return ItemStack.EMPTY;
        }
      } else {
        return ItemStack.EMPTY;
      }
    } else if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) {
      slot.set(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    return original;
  }

  public static boolean isFootPart(ItemStack stack) {
    return stack.is(ModContent.WHEEL_FOOT_PART)
        || stack.is(ModContent.CRAWLER_FOOT_PART)
        || stack.is(ModContent.HOVER_FOOT_PART);
  }

  public static boolean isTopModule(ItemStack stack) {
    return stack.is(ModContent.PROXIMITY_SENSOR_MODULE);
  }

  public static boolean isTool(ItemStack stack) {
    return stack.has(DataComponents.TOOL);
  }

  private static final class EquipmentSlot extends Slot {
    EquipmentSlot(Container container, int slot, int x, int y) {
      super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return switch (getContainerSlot()) {
        case TurtleBlockEntity.EQUIPMENT_FOOT -> isFootPart(stack);
        case TurtleBlockEntity.EQUIPMENT_ARM -> isTool(stack);
        case TurtleBlockEntity.EQUIPMENT_TOP -> isTopModule(stack);
        default -> false;
      };
    }

    @Override
    public int getMaxStackSize() {
      return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
      return 1;
    }
  }
}
