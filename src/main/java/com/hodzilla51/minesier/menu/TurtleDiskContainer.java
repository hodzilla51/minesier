package com.hodzilla51.minesier.menu;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Single-slot container view over a turtle's inserted program disk. */
public final class TurtleDiskContainer implements Container {
  private final TurtleBlockEntity turtle;

  public TurtleDiskContainer(TurtleBlockEntity turtle) {
    this.turtle = turtle;
  }

  @Override
  public int getContainerSize() {
    return 1;
  }

  @Override
  public boolean isEmpty() {
    return turtle.getDisk().isEmpty();
  }

  @Override
  public ItemStack getItem(int slot) {
    return slot == 0 ? turtle.getDisk() : ItemStack.EMPTY;
  }

  @Override
  public ItemStack removeItem(int slot, int amount) {
    if (slot != 0 || amount <= 0) {
      return ItemStack.EMPTY;
    }
    ItemStack disk = turtle.getDisk();
    if (disk.isEmpty()) {
      return ItemStack.EMPTY;
    }
    ItemStack removed = disk.split(amount);
    turtle.setDisk(disk.isEmpty() ? ItemStack.EMPTY : disk);
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    if (slot != 0) {
      return ItemStack.EMPTY;
    }
    ItemStack disk = turtle.getDisk();
    turtle.setDisk(ItemStack.EMPTY);
    return disk;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    if (slot == 0) {
      turtle.setDisk(stack.is(ModContent.DISK) ? stack.copyWithCount(1) : ItemStack.EMPTY);
    }
  }

  @Override
  public void setChanged() {
    turtle.markChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return true;
  }

  @Override
  public void clearContent() {
    turtle.setDisk(ItemStack.EMPTY);
  }
}
