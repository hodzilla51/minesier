package com.hodzilla51.minesier.menu;

import com.hodzilla51.minesier.block.TurtleBlockEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Opens the Turtle expansion-parts menu and syncs the turtle position to the client. */
public record TurtleEquipmentMenuProvider(
    TurtleBlockEntity turtle, BlockPos pos, int screenWidth, int screenHeight)
    implements ExtendedMenuProvider<TurtleMenuData> {

  @Override
  public TurtleMenuData getScreenOpeningData(ServerPlayer player) {
    return new TurtleMenuData(pos, screenWidth, screenHeight);
  }

  @Override
  public Component getDisplayName() {
    return Component.literal("Turtle equipment");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new TurtleEquipmentMenu(
        id,
        inventory,
        new TurtleEquipmentContainer(turtle),
        ContainerLevelAccess.create(player.level(), pos),
        screenWidth,
        screenHeight);
  }
}
