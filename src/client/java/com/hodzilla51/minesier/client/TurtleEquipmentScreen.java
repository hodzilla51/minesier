package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.menu.TurtleEquipmentMenu;
import com.hodzilla51.minesier.net.OpenTurtleInventoryC2S;
import com.hodzilla51.minesier.net.OpenTurtleTerminalC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Full-screen Turtle expansion-parts UI. */
public class TurtleEquipmentScreen extends AbstractContainerScreen<TurtleEquipmentMenu> {
  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22;
  private static final int TAB_H = 16;
  private static final int TAB_W = 80;
  private static final int PLAYER_INV_H = 72;
  private static final int SLOT = 18;

  private static final int PANEL_COLOR = 0xFF0C120C;
  private static final int BORDER_COLOR = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR = 0xFF7CFC7C;
  private static final int TEXT_COLOR = 0xFFD0E8D0;
  private static final int EMPTY_COLOR = 0xFF5A6E5A;
  private static final int CELL_COLOR = 0xFF243024;

  public TurtleEquipmentScreen(TurtleEquipmentMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 256, 216);
  }

  @Override
  protected void init() {
    super.init();
    this.leftPos = 0;
    this.topPos = 0;
    addRenderableWidget(
        Button.builder(Component.literal("Terminal"), b -> returnToTerminal())
            .bounds(MARGIN, 2, TAB_W, TAB_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Storage"), b -> openStorage())
            .bounds(MARGIN + TAB_W + 4, 2, TAB_W, TAB_H)
            .build());
  }

  private int playerInventoryTop() {
    return this.height - MARGIN - PLAYER_INV_H;
  }

  private int panelBottom() {
    return playerInventoryTop() - 18;
  }

  private int titleHeight() {
    return this.font.lineHeight + 6;
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    int left = MARGIN;
    int right = this.width - MARGIN;
    int top = CONTENT_TOP;
    int bottom = panelBottom();
    int titleHeight = titleHeight();

    graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, bottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(this.font, "Turtle expansion parts", left + 6, top + 4, TITLE_COLOR);

    drawEquipmentSlot(
        graphics,
        TurtleBlockEntity.EQUIPMENT_FOOT,
        "Foot",
        "Wheel, crawler, hover",
        mouseX,
        mouseY);
    drawEquipmentSlot(
        graphics, TurtleBlockEntity.EQUIPMENT_ARM, "Arm", "Mining tool", mouseX, mouseY);
    drawEquipmentSlot(
        graphics, TurtleBlockEntity.EQUIPMENT_TOP, "Top", "Utility module", mouseX, mouseY);

    int inventoryLeft = (this.width - 9 * SLOT) / 2;
    int inventoryTop = playerInventoryTop();
    graphics.text(this.font, "Your inventory", inventoryLeft, inventoryTop - 12, TITLE_COLOR);
    for (int i = 0; i < TurtleEquipmentMenu.PLAYER_END; i++) {
      Slot slot = this.menu.getSlot(i);
      graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, BORDER_COLOR);
      graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, CELL_COLOR);
    }
  }

  private void drawEquipmentSlot(
      GuiGraphicsExtractor graphics,
      int equipmentSlot,
      String title,
      String hint,
      int mouseX,
      int mouseY) {
    Slot slot = this.menu.getSlot(TurtleEquipmentMenu.EQUIPMENT_START + equipmentSlot);
    int cardW = 160;
    int cardH = 48;
    int cardX = slot.x - 12;
    int cardY = slot.y - 10;
    graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, BORDER_COLOR);
    graphics.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1, CELL_COLOR);
    graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, BORDER_COLOR);
    graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, PANEL_COLOR);
    graphics.text(this.font, title, slot.x + 24, slot.y - 1, TITLE_COLOR);

    ItemStack stack = this.menu.equipmentItem(equipmentSlot);
    String detail = stack.isEmpty() ? hint : itemName(stack);
    int color = stack.isEmpty() ? EMPTY_COLOR : TEXT_COLOR;
    graphics.text(this.font, trim(detail, cardW - 34), slot.x + 24, slot.y + 11, color);
  }

  private static String itemName(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
  }

  private String trim(String text, int maxPx) {
    int max = Math.max(1, maxPx / 6);
    return text.length() > max ? text.substring(0, max) : text;
  }

  private void returnToTerminal() {
    ClientPlayNetworking.send(new OpenTurtleTerminalC2S(this.menu.turtlePos()));
  }

  private void openStorage() {
    ClientPlayNetworking.send(
        new OpenTurtleInventoryC2S(this.menu.turtlePos(), this.width, this.height));
  }

  protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
    return false;
  }
}
