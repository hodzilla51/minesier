package com.hodzilla51.minesier.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Read-only managed switch status screen. */
public class SwitchStatusScreen extends Screen {
  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22;
  private static final int BUTTON_H = 20;

  private static final int PANEL_COLOR = 0xFF0C120C;
  private static final int BORDER_COLOR = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR = 0xFF7CFC7C;
  private static final int TEXT_COLOR = 0xFFD0E8D0;

  private final BlockPos pos;
  private final String status;

  public SwitchStatusScreen(BlockPos pos, String status) {
    super(Component.literal("Switch"));
    this.pos = pos;
    this.status = status;
  }

  @Override
  protected void init() {
    addRenderableWidget(
        Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(this.width - MARGIN - 56, this.height - MARGIN - BUTTON_H, 56, BUTTON_H)
            .build());
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    Font font = Minecraft.getInstance().font;
    int left = MARGIN;
    int right = this.width - MARGIN;
    int top = CONTENT_TOP;
    int bottom = this.height - MARGIN - BUTTON_H - 6;
    int titleHeight = font.lineHeight + 6;

    graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, bottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(font, "Switch status " + posLabel(), left + 6, top + 4, TITLE_COLOR);

    int lineHeight = font.lineHeight + 1;
    int y = top + titleHeight + 4;
    int maxChars = Math.max(1, (right - left - 12) / 6);
    int maxLines = Math.max(0, (bottom - 4 - y) / lineHeight);
    String[] lines = status.split("\n", -1);
    for (int i = 0; i < lines.length && i < maxLines; i++) {
      String line = lines[i];
      if (line.length() > maxChars) {
        line = line.substring(0, maxChars);
      }
      graphics.text(font, line, left + 6, y + i * lineHeight, TEXT_COLOR);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private String posLabel() {
    return pos.getX() + "," + pos.getY() + "," + pos.getZ();
  }
}
