package com.hodzilla51.minesier.menu;

import com.hodzilla51.minesier.block.TurtleBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Container view over a turtle's equipment slots. */
public final class TurtleEquipmentContainer implements Container {
  private final TurtleBlockEntity turtle;

  public TurtleEquipmentContainer(TurtleBlockEntity turtle) {
    this.turtle = turtle;
  }

  @Override
  public int getContainerSize() {
    return TurtleBlockEntity.EQUIPMENT_SIZE;
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack stack : turtle.getEquipment()) {
      if (!stack.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public ItemStack getItem(int slot) {
    return turtle.getEquipment().get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int amount) {
    ItemStack removed = turtle.getEquipment().get(slot).split(amount);
    if (!removed.isEmpty()) {
      setChanged();
    }
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    ItemStack removed = turtle.getEquipment().get(slot);
    turtle.getEquipment().set(slot, ItemStack.EMPTY);
    return removed;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    turtle.getEquipment().set(slot, stack);
    if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
      stack.setCount(getMaxStackSize());
    }
    setChanged();
  }

  @Override
  public void setChanged() {
    turtle.equipmentChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return true;
  }

  @Override
  public void clearContent() {
    turtle.getEquipment().clear();
    setChanged();
  }
}
