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
 * pickup, shift-click, dupe-safe sync — all free). The turtle's 16 slots are also real slots so
 * their contents sync to the client and merge via {@link #moveItemStackTo}, but they live
 * off-screen: the {@link com.hodzilla51.minesier.client.TurtleScreen} draws them as a
 * terminal-style text list and routes clicks through {@code TurtleClickC2S} so dropping anywhere in
 * the box first-fits into the turtle. Edits are blocked while a program is running.
 */
public class TurtleMenu extends AbstractContainerMenu {
  public static final int TURTLE_SIZE = 16;
  public static final int PLAYER_START = 0;
  public static final int PLAYER_END = 36; // 27 main + 9 hotbar
  public static final int TURTLE_START = PLAYER_END;
  public static final int TURTLE_END = TURTLE_START + TURTLE_SIZE;

  private static final int OFFSCREEN = -3000;

  private final ContainerLevelAccess access;
  private final BlockPos pos;

  /** Client constructor: synchronized position and viewport dimensions determine slot layout. */
  public TurtleMenu(int id, Inventory playerInv, TurtleMenuData data) {
    this(
        id,
        playerInv,
        new SimpleContainer(TURTLE_SIZE),
        ContainerLevelAccess.NULL,
        data.pos(),
        data.screenWidth(),
        data.screenHeight());
  }

  /** Server constructor: backed by the live turtle inventory. */
  public TurtleMenu(int id, Inventory playerInv, Container turtle, ContainerLevelAccess access) {
    this(
        id,
        playerInv,
        turtle,
        access,
        access.evaluate((level, position) -> position, BlockPos.ZERO),
        0,
        0);
  }

  public TurtleMenu(
      int id,
      Inventory playerInv,
      Container turtle,
      ContainerLevelAccess access,
      int screenWidth,
      int screenHeight) {
    this(
        id,
        playerInv,
        turtle,
        access,
        access.evaluate((level, position) -> position, BlockPos.ZERO),
        screenWidth,
        screenHeight);
  }

  private TurtleMenu(
      int id,
      Inventory playerInv,
      Container turtle,
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

    // Turtle storage: real slots (for sync + merge) parked off-screen; the screen draws them.
    for (int i = 0; i < TURTLE_SIZE; i++) {
      addSlot(new Slot(turtle, i, OFFSCREEN, OFFSCREEN));
    }
  }

  /** The current contents of a turtle slot, for the screen's text list. */
  public ItemStack turtleItem(int slot) {
    return getSlot(TURTLE_START + slot).getItem();
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
            ? moveItemStackTo(stack, TURTLE_START, TURTLE_END, false) // player -> turtle
            : moveItemStackTo(stack, PLAYER_START, PLAYER_END, false); // turtle -> player
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
}
