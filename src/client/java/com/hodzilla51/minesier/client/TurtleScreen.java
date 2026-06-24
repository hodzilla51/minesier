package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.menu.TurtleMenu;
import com.hodzilla51.minesier.net.OpenTurtleTerminalC2S;
import com.hodzilla51.minesier.net.TurtleClickC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen turtle storage UI. It deliberately mirrors {@link ComputerScreen}'s tab row and panel
 * layout while retaining a real {@link TurtleMenu} for vanilla item synchronization, cursor
 * handling, and shift-click behavior.
 */
public class TurtleScreen extends AbstractContainerScreen<TurtleMenu> {
  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22;
  private static final int TAB_H = 16;
  private static final int TAB_W = 80;
  private static final int PLAYER_INV_H = 72;
  private static final int SLOT = 18;
  private static final int ROW_H = 11;
  private static final int ROWS = 8;

  private static final int PANEL_COLOR = 0xFF0C120C;
  private static final int BORDER_COLOR = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR = 0xFF7CFC7C;
  private static final int TEXT_COLOR = 0xFFD0E8D0;
  private static final int EMPTY_COLOR = 0xFF5A6E5A;
  private static final int HOVER_COLOR = 0x33FFFFFF;
  private static final int CELL_COLOR = 0xFF243024;

  private boolean pressInStorage;
  private boolean pressCarriedEmpty;

  public TurtleScreen(TurtleMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 256, 216);
  }

  @Override
  protected void init() {
    super.init();

    // AbstractContainerScreen normally centers a fixed 256x216 texture. This screen has no such
    // texture: its panels use the full viewport, matching ComputerScreen exactly.
    this.leftPos = 0;
    this.topPos = 0;
    addRenderableWidget(
        Button.builder(Component.literal("Terminal"), b -> returnToTerminal())
            .bounds(MARGIN, 2, TAB_W, TAB_H)
            .build());
  }

  private int playerInventoryTop() {
    return this.height - MARGIN - PLAYER_INV_H;
  }

  private int storageBottom() {
    return playerInventoryTop() - 18;
  }

  private int titleHeight() {
    return this.font.lineHeight + 6;
  }

  private int cellWidth() {
    return (this.width - 2 * MARGIN - 8) / 2;
  }

  private int cellX(int index) {
    return MARGIN + 4 + (index / ROWS) * cellWidth();
  }

  private int cellY(int index) {
    return CONTENT_TOP + titleHeight() + 4 + (index % ROWS) * ROW_H;
  }

  private int cellAt(double x, double y) {
    for (int i = 0; i < TurtleMenu.TURTLE_SIZE; i++) {
      int cellX = cellX(i);
      int cellY = cellY(i);
      if (x >= cellX && x < cellX + cellWidth() && y >= cellY && y < cellY + ROW_H) {
        return i;
      }
    }
    return -1;
  }

  private boolean inStorage(double x, double y) {
    return x >= MARGIN && x < this.width - MARGIN && y >= CONTENT_TOP && y < storageBottom();
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    int left = MARGIN;
    int right = this.width - MARGIN;
    int top = CONTENT_TOP;
    int bottom = storageBottom();
    int titleHeight = titleHeight();

    graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, bottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(this.font, "Turtle storage", left + 6, top + 4, TITLE_COLOR);

    int hover = cellAt(mouseX, mouseY);
    for (int i = 0; i < TurtleMenu.TURTLE_SIZE; i++) {
      int x = cellX(i);
      int y = cellY(i);
      if (i == hover) {
        graphics.fill(x, y, x + cellWidth(), y + ROW_H, HOVER_COLOR);
      }
      ItemStack stack = this.menu.turtleItem(i);
      String label;
      int color;
      if (stack.isEmpty()) {
        label = String.format("%2d: -", i + 1);
        color = EMPTY_COLOR;
      } else {
        label = String.format("%2d: %s x%d", i + 1, itemName(stack), stack.getCount());
        color = TEXT_COLOR;
      }
      graphics.text(this.font, trim(label, cellWidth() - 4), x + 2, y + 2, color);
    }

    int inventoryLeft = (this.width - 9 * SLOT) / 2;
    int inventoryTop = playerInventoryTop();
    graphics.text(this.font, "Your inventory", inventoryLeft, inventoryTop - 12, TITLE_COLOR);
    for (int i = 0; i < TurtleMenu.PLAYER_END; i++) {
      Slot slot = this.menu.getSlot(i);
      graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, BORDER_COLOR);
      graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, CELL_COLOR);
    }
  }

  private static String itemName(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
  }

  private String trim(String text, int maxPx) {
    int max = Math.max(1, maxPx / 6);
    return text.length() > max ? text.substring(0, max) : text;
  }

  /** Closes the storage menu, then reopens the terminal for this same turtle. */
  private void returnToTerminal() {
    ClientPlayNetworking.send(new OpenTurtleTerminalC2S(this.menu.turtlePos()));
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    pressInStorage = event.button() == 0 && inStorage(event.x(), event.y());
    pressCarriedEmpty = this.menu.getCarried().isEmpty();
    if (pressInStorage) {
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (event.button() == 0) {
      boolean overStorage = inStorage(event.x(), event.y());
      boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
      if (pressInStorage && overStorage) {
        if (pressCarriedEmpty) {
          int cell = cellAt(event.x(), event.y());
          if (cell >= 0) {
            ClientPlayNetworking.send(new TurtleClickC2S(cell, shift));
          }
        } else {
          ClientPlayNetworking.send(new TurtleClickC2S(-1, false));
        }
        pressInStorage = false;
        return true;
      }
      if (!pressInStorage && overStorage && !this.menu.getCarried().isEmpty()) {
        ClientPlayNetworking.send(new TurtleClickC2S(-1, false));
        return true;
      }
    }
    return super.mouseReleased(event);
  }

  /** The fixed backing texture no longer bounds the full-screen UI. */
  protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
    return false;
  }
}
