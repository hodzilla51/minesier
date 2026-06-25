package com.hodzilla51.minesier.menu;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.turtle.TurtleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Storage menu for a turtle. The player's own inventory uses ordinary vanilla slots (icons, cursor
 * pickup, shift-click, dupe-safe sync — all free). The turtle's 16 storage slots and disk slot are
 * also real slots, with edits blocked while a program is running.
 */
public class TurtleMenu extends AbstractContainerMenu {
  public static final int TURTLE_SIZE = 16;
  public static final int PLAYER_START = 0;
  public static final int PLAYER_END = 36; // 27 main + 9 hotbar
  public static final int TURTLE_START = PLAYER_END;
  public static final int TURTLE_END = TURTLE_START + TURTLE_SIZE;
  public static final int DISK_SLOT = TURTLE_END;
  public static final int DISK_END = DISK_SLOT + 1;

  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22;
  private static final int SLOT = 18;
  private static final int TURTLE_GRID_X = MARGIN + 8;
  private static final int TURTLE_GRID_Y = CONTENT_TOP + 30;
  private static final int TURTLE_GRID_COLS = 4;

  private final ContainerLevelAccess access;
  private final BlockPos pos;

  /** Client constructor: synchronized position and viewport dimensions determine slot layout. */
  public TurtleMenu(int id, Inventory playerInv, TurtleMenuData data) {
    this(
        id,
        playerInv,
        new SimpleContainer(TURTLE_SIZE),
        new SimpleContainer(1),
        ContainerLevelAccess.NULL,
        data.pos(),
        data.screenWidth(),
        data.screenHeight());
  }

  /** Server constructor: backed by the live turtle inventory. */
  public TurtleMenu(
      int id, Inventory playerInv, Container turtle, Container disk, ContainerLevelAccess access) {
    this(
        id,
        playerInv,
        turtle,
        disk,
        access,
        access.evaluate((level, position) -> position, BlockPos.ZERO),
        0,
        0);
  }

  public TurtleMenu(
      int id,
      Inventory playerInv,
      Container turtle,
      Container disk,
      ContainerLevelAccess access,
      int screenWidth,
      int screenHeight) {
    this(
        id,
        playerInv,
        turtle,
        disk,
        access,
        access.evaluate((level, position) -> position, BlockPos.ZERO),
        screenWidth,
        screenHeight);
  }

  private TurtleMenu(
      int id,
      Inventory playerInv,
      Container turtle,
      Container disk,
      ContainerLevelAccess access,
      BlockPos pos,
      int screenWidth,
      int screenHeight) {
    super(ModContent.TURTLE_MENU, id);
    this.access = access;
    this.pos = pos;

    int inventoryX = (Math.max(256, screenWidth) - 9 * 18) / 2;
    int inventoryY = Math.max(216, screenHeight) - 12 - 72;
    // Player inventory: 3 main rows then the hotbar (slot indices 0..35).
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        addSlot(
            new Slot(playerInv, 9 + row * 9 + col, inventoryX + col * 18, inventoryY + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      addSlot(new Slot(playerInv, col, inventoryX + col * 18, inventoryY + 58));
    }

    // Turtle storage: real slots for sync, cursor handling, and shift-click merge.
    for (int i = 0; i < TURTLE_SIZE; i++) {
      addSlot(new Slot(turtle, i, turtleSlotX(i), turtleSlotY(i)));
    }
    addSlot(new DiskSlot(disk, diskSlotX(screenWidth), diskSlotY()));
  }

  public static int turtleSlotX(int slot) {
    return TURTLE_GRID_X + (slot % TURTLE_GRID_COLS) * SLOT;
  }

  public static int turtleSlotY(int slot) {
    return TURTLE_GRID_Y + (slot / TURTLE_GRID_COLS) * SLOT;
  }

  public static int diskSlotX(int screenWidth) {
    return Math.max(256, screenWidth) - MARGIN - 34;
  }

  public static int diskSlotY() {
    return CONTENT_TOP + 30;
  }

  /** The current contents of a turtle slot. */
  public ItemStack turtleItem(int slot) {
    return getSlot(TURTLE_START + slot).getItem();
  }

  /** The disk-slot item shown in the storage screen. */
  public ItemStack diskItem() {
    return getSlot(DISK_SLOT).getItem();
  }

  /** Turtle position associated with this menu, including on the client. */
  public BlockPos turtlePos() {
    return pos;
  }

  private boolean running() {
    return access.evaluate((level, p) -> TurtleManager.isRunning(level, p), false);
  }

  @Override
  public boolean stillValid(Player player) {
    return stillValid(access, player, ModContent.TURTLE_BLOCK)
        && access.evaluate((level, p) -> !TurtleManager.isRunning(level, p), true);
  }

  /** Server: first-fit + merge the cursor's carried stack into the turtle (drop-anywhere). */
  public void storeCarried() {
    if (running()) {
      return;
    }
    ItemStack carried = getCarried();
    if (carried.isEmpty()) {
      return;
    }
    moveItemStackTo(carried, TURTLE_START, TURTLE_END, false);
    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
    broadcastChanges();
  }

  /**
   * Server: take from a turtle slot — to the player inventory when {@code shift}, else the cursor.
   */
  public void take(Player player, int slot, boolean shift) {
    if (running() || slot < 0 || slot >= TURTLE_SIZE) {
      return;
    }
    int index = TURTLE_START + slot;
    if (shift) {
      quickMoveStack(player, index);
      broadcastChanges();
      return;
    }
    Slot s = getSlot(index);
    ItemStack inSlot = s.getItem();
    if (inSlot.isEmpty()) {
      return;
    }
    ItemStack carried = getCarried();
    if (carried.isEmpty()) {
      setCarried(inSlot);
      s.set(ItemStack.EMPTY);
    } else if (ItemStack.isSameItemSameComponents(carried, inSlot)) {
      int moved = Math.min(carried.getMaxStackSize() - carried.getCount(), inSlot.getCount());
      carried.grow(moved);
      inSlot.shrink(moved);
      s.set(inSlot.isEmpty() ? ItemStack.EMPTY : inSlot);
      setCarried(carried);
    } else {
      setCarried(inSlot);
      s.set(carried);
    }
    broadcastChanges();
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (running()) {
      return ItemStack.EMPTY;
    }
    Slot slot = getSlot(index);
    if (slot == null || !slot.hasItem()) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    boolean moved =
        index < TURTLE_START
            ? moveFromPlayer(stack)
            : moveItemStackTo(stack, PLAYER_START, PLAYER_END, false); // turtle/disk -> player
    if (!moved) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) {
      slot.set(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    return original;
  }

  private boolean moveFromPlayer(ItemStack stack) {
    if (stack.is(ModContent.DISK) && moveItemStackTo(stack, DISK_SLOT, DISK_END, false)) {
      return true;
    }
    return moveItemStackTo(stack, TURTLE_START, TURTLE_END, false);
  }

  private static final class DiskSlot extends Slot {
    DiskSlot(Container container, int x, int y) {
      super(container, 0, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      return stack.is(ModContent.DISK);
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
