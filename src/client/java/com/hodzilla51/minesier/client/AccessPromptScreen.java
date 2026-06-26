package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.block.AccessControlledBlockEntity;
import com.hodzilla51.minesier.net.AccessActionC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Credential prompt for one MineSIer device. */
public class AccessPromptScreen extends Screen {
  private static final int PANEL_W = 280;
  private static final int PANEL_H = 104;
  private static final int BUTTON_H = 20;

  private static final int PANEL_COLOR = 0xFF0C120C;
  private static final int BORDER_COLOR = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR = 0xFF7CFC7C;
  private static final int TEXT_COLOR = 0xFFD0E8D0;

  private final BlockPos pos;
  private final String mode;
  private EditBox password;

  public AccessPromptScreen(BlockPos pos, String mode) {
    super(Component.literal("Access"));
    this.pos = pos;
    this.mode = mode;
  }

  @Override
  protected void init() {
    int left = (this.width - PANEL_W) / 2;
    int top = (this.height - PANEL_H) / 2;
    this.password =
        new EditBox(
            Minecraft.getInstance().font,
            left + 10,
            top + 42,
            PANEL_W - 20,
            20,
            Component.literal("password"));
    this.password.setHint(Component.literal("password"));
    this.password.setMaxLength(96);
    addRenderableWidget(this.password);

    int y = top + PANEL_H - BUTTON_H - 8;
    int bw = AccessControlledBlockEntity.MODE_PASSWORD.equals(mode) ? 62 : 72;
    int gap = 4;
    int x = left + 10;
    if (AccessControlledBlockEntity.MODE_PASSWORD.equals(mode)) {
      addRenderableWidget(
          Button.builder(Component.literal("Unlock"), b -> send(AccessActionC2S.UNLOCK))
              .bounds(x, y, bw, BUTTON_H)
              .build());
      x += bw + gap;
    }
    addRenderableWidget(
        Button.builder(Component.literal("Set"), b -> send(AccessActionC2S.SET_PASSWORD))
            .bounds(x, y, bw, BUTTON_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Public"), b -> send(AccessActionC2S.MAKE_PUBLIC))
            .bounds(x + bw + gap, y, bw, BUTTON_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(left + PANEL_W - 10 - bw, y, bw, BUTTON_H)
            .build());
    setInitialFocus(this.password);
  }

  private void send(int action) {
    ClientPlayNetworking.send(new AccessActionC2S(pos, action, password.getValue()));
    onClose();
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    Font font = Minecraft.getInstance().font;
    int left = (this.width - PANEL_W) / 2;
    int top = (this.height - PANEL_H) / 2;
    int titleHeight = font.lineHeight + 6;
    graphics.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, BORDER_COLOR);
    graphics.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL_COLOR);
    graphics.fill(left, top, left + PANEL_W, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(font, title(), left + 8, top + 4, TITLE_COLOR);
    graphics.text(font, hint(), left + 10, top + 28, TEXT_COLOR);
  }

  private String title() {
    if (AccessControlledBlockEntity.MODE_PASSWORD.equals(mode)) {
      return "Device password";
    }
    return "Configure device access";
  }

  private String hint() {
    if (AccessControlledBlockEntity.MODE_PASSWORD.equals(mode)) {
      return "Enter password, then interact again.";
    }
    return "Set a password or make this device public.";
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
