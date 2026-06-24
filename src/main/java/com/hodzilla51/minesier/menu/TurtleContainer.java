package com.hodzilla51.minesier.menu;

import com.hodzilla51.minesier.block.TurtleBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A live {@link Container} view over a turtle block entity's 16-slot inventory, so a vanilla menu
 * can drive it with dupe-safe slot machinery. {@link #setChanged()} marks the block entity dirty so
 * edits persist. Validity (reach, running state) is enforced by the menu, not here.
 */
public final class TurtleContainer implements Container {
  private final NonNullList<ItemStack> items;
  private final TurtleBlockEntity turtle;

  public TurtleContainer(TurtleBlockEntity turtle) {
    this.turtle = turtle;
    this.items = turtle.getInventory();
  }

  @Override
  public int getContainerSize() {
    return items.size();
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack stack : items) {
      if (!stack.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public ItemStack getItem(int slot) {
    return items.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int amount) {
    ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
    if (!removed.isEmpty()) {
      setChanged();
    }
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    return ContainerHelper.takeItem(items, slot);
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    items.set(slot, stack);
  }

  @Override
  public void setChanged() {
    turtle.markChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return true; // the menu owns reach + running checks
  }

  @Override
  public void clearContent() {
    items.clear();
    setChanged();
  }
}
