package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.net.ProgramActionC2S;
import com.hodzilla51.minesier.net.RequestInventoryC2S;
import com.hodzilla51.minesier.net.RunCommandC2S;
import com.hodzilla51.minesier.net.TurtleInventoryActionC2S;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The computer/turtle screen. A tab bar switches between the JS terminal (scrollback + multi-line
 * editor + program buttons) and, for turtles, a read-only inventory viewer. Keeping them on
 * separate tabs avoids cramming the already-full terminal.
 */
public class ComputerScreen extends Screen {
  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22; // leaves room for the tab row
  private static final int EDITOR_HEIGHT = 72;
  private static final int BUTTON_H = 20;
  private static final int PANE_W = 104; // left file-tree pane width
  private static final int PANE_GAP = 6;
  private static final int TAB_TERMINAL = 0;
  private static final int TAB_INVENTORY = 1;

  // Panel skin colors (ARGB).
  private static final int PANEL_COLOR = 0xFF0C120C;
  private static final int BORDER_COLOR = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR = 0xFF7CFC7C;
  private static final int TEXT_COLOR = 0xFFD0E8D0;
  private static final int SELECTED_COLOR = 0xFFF5E96A;

  /** The currently open screen, if any (Minecraft no longer exposes the active screen). */
  private static ComputerScreen open;

  private BlockPos pos;
  private String transcript;
  private final boolean turtle;

  private int tab = TAB_TERMINAL;
  private MultiLineEditBox editor;
  private EditBox nameField;
  private final List<AbstractWidget> terminalWidgets = new ArrayList<>();
  private final List<AbstractWidget> inventoryWidgets = new ArrayList<>();

  private int invSelected = -1;
  private String[] invSlots = new String[0];

  /** Program names from the inserted disk, flattened into clickable file-tree rows. */
  private String[] programs = new String[0];

  private final List<FileRow> fileRows = new ArrayList<>();
  private int paneRowsTop; // y of the first file-tree row (set during render, read on click)
  private int paneRowHeight;

  /** One line of the file tree: a folder ({@code path == null}) or a clickable file. */
  private record FileRow(int depth, String label, String path) {}

  /** One folder level while building the tree: sub-folders and files, both sorted. */
  private static final class Dir {
    final TreeMap<String, Dir> dirs = new TreeMap<>();
    final TreeSet<String> files = new TreeSet<>();
  }

  public ComputerScreen(BlockPos pos, String transcript, boolean turtle) {
    super(Component.literal("Computer"));
    this.pos = pos;
    this.transcript = transcript;
    this.turtle = turtle;
  }

  /** Backwards-compatible: a non-turtle terminal. */
  public ComputerScreen(BlockPos pos, String transcript) {
    this(pos, transcript, false);
  }

  /** Applies a server transcript update to the open screen, re-keying its target position. */
  public static void updateIfOpen(BlockPos pos, String transcript) {
    if (open != null) {
      open.pos = pos;
      open.transcript = transcript;
    }
  }

  /** Loads a server-sent program source into the open terminal's editor. */
  public static void loadIntoEditor(String source) {
    if (open != null && open.editor != null) {
      open.editor.setValue(source);
    }
  }

  /** Applies the disk's program names to the open screen's file-tree pane. */
  public static void showPrograms(String names) {
    if (open != null) {
      open.programs = names.isEmpty() ? new String[0] : names.split("\n", -1);
      open.rebuildFileRows();
    }
  }

  /** Rebuilds the flattened, clickable file-tree rows from {@link #programs}. */
  private void rebuildFileRows() {
    fileRows.clear();
    Dir root = new Dir();
    for (String name : programs) {
      if (name.isBlank()) {
        continue;
      }
      Dir node = root;
      String[] parts = name.split("/");
      for (int i = 0; i < parts.length - 1; i++) {
        node = node.dirs.computeIfAbsent(parts[i], k -> new Dir());
      }
      node.files.add(parts[parts.length - 1]);
    }
    flattenDir(root, "", 0);
  }

  private void flattenDir(Dir node, String prefix, int depth) {
    for (var entry : node.dirs.entrySet()) {
      fileRows.add(new FileRow(depth, entry.getKey() + "/", null));
      flattenDir(entry.getValue(), prefix + entry.getKey() + "/", depth + 1);
    }
    for (String file : node.files) {
      fileRows.add(new FileRow(depth, file, prefix + file));
    }
  }

  /** Shows a server-sent inventory snapshot in the open screen's inventory tab. */
  public static void showInventory(int selected, String slots) {
    if (open != null) {
      open.invSelected = selected;
      open.invSlots = slots.split("\n", -1);
    }
  }

  @Override
  protected void init() {
    Font font = Minecraft.getInstance().font;
    terminalWidgets.clear();
    inventoryWidgets.clear();

    // Tab row.
    addRenderableWidget(
        Button.builder(Component.literal("Terminal"), b -> setTab(TAB_TERMINAL))
            .bounds(MARGIN, 2, 64, 16)
            .build());
    if (turtle) {
      addRenderableWidget(
          Button.builder(Component.literal("Inventory"), b -> setTab(TAB_INVENTORY))
              .bounds(MARGIN + 68, 2, 64, 16)
              .build());
    }

    int contentLeft = MARGIN + PANE_W + PANE_GAP;
    int editorWidth = this.width - contentLeft - MARGIN;
    int buttonY = this.height - MARGIN - BUTTON_H;
    int editorY = buttonY - 4 - EDITOR_HEIGHT;

    this.editor =
        MultiLineEditBox.builder()
            .setX(contentLeft)
            .setY(editorY)
            .setShowBackground(true)
            .setShowDecorations(true)
            .build(
                font,
                editorWidth,
                EDITOR_HEIGHT,
                Component.literal("JS program — Ctrl/Cmd+Enter to run"));
    this.editor.setCharacterLimit(8192);
    addRenderableWidget(this.editor);
    terminalWidgets.add(this.editor);

    // Bottom row, right-aligned: [name field] Save Load List Eject Run
    int gap = 4;
    int bw = 44;
    int runX = this.width - MARGIN - bw;
    int ejectX = runX - gap - bw;
    int listX = ejectX - gap - bw;
    int loadX = listX - gap - bw;
    int saveX = loadX - gap - bw;
    int nameW = saveX - gap - MARGIN;

    this.nameField = new EditBox(font, MARGIN, buttonY, nameW, BUTTON_H, Component.literal("name"));
    this.nameField.setHint(Component.literal("program name"));
    this.nameField.setMaxLength(64);
    addRenderableWidget(this.nameField);
    terminalWidgets.add(this.nameField);

    terminalWidgets.add(
        addRenderableWidget(
            Button.builder(Component.literal("Save"), b -> program(ProgramActionC2S.SAVE))
                .bounds(saveX, buttonY, bw, BUTTON_H)
                .build()));
    terminalWidgets.add(
        addRenderableWidget(
            Button.builder(Component.literal("Load"), b -> program(ProgramActionC2S.LOAD))
                .bounds(loadX, buttonY, bw, BUTTON_H)
                .build()));
    terminalWidgets.add(
        addRenderableWidget(
            Button.builder(Component.literal("List"), b -> program(ProgramActionC2S.LIST))
                .bounds(listX, buttonY, bw, BUTTON_H)
                .build()));
    terminalWidgets.add(
        addRenderableWidget(
            Button.builder(Component.literal("Eject"), b -> program(ProgramActionC2S.EJECT))
                .bounds(ejectX, buttonY, bw, BUTTON_H)
                .build()));
    terminalWidgets.add(
        addRenderableWidget(
            Button.builder(Component.literal("Run"), b -> runCurrent())
                .bounds(runX, buttonY, bw, BUTTON_H)
                .build()));

    if (turtle) {
      int slotW = 64;
      int slotH = 20;
      int gridLeft = MARGIN + 8;
      int gridTop = CONTENT_TOP + 30;
      for (int slot = 0; slot < 16; slot++) {
        int index = slot;
        int x = gridLeft + (slot % 4) * (slotW + 4);
        int y = gridTop + (slot / 4) * (slotH + 16);
        inventoryWidgets.add(
            addRenderableWidget(
                Button.builder(
                        Component.literal("Slot " + (slot + 1)),
                        b -> inventoryAction(TurtleInventoryActionC2S.SELECT, index))
                    .bounds(x, y, slotW, slotH)
                    .build()));
      }
      inventoryWidgets.add(
          addRenderableWidget(
              Button.builder(
                      Component.literal("Insert held"),
                      b -> inventoryAction(TurtleInventoryActionC2S.INSERT_HELD, invSelected))
                  .bounds(MARGIN + 8, this.height - MARGIN - BUTTON_H, 104, BUTTON_H)
                  .build()));
      inventoryWidgets.add(
          addRenderableWidget(
              Button.builder(
                      Component.literal("Extract selected"),
                      b -> inventoryAction(TurtleInventoryActionC2S.EXTRACT, invSelected))
                  .bounds(MARGIN + 116, this.height - MARGIN - BUTTON_H, 120, BUTTON_H)
                  .build()));
    }

    applyTab();
    open = this;
  }

  private void setTab(int which) {
    this.tab = which;
    applyTab();
    if (which == TAB_INVENTORY) {
      ClientPlayNetworking.send(new RequestInventoryC2S(this.pos));
    }
  }

  private void applyTab() {
    boolean terminal = tab == TAB_TERMINAL;
    for (AbstractWidget widget : terminalWidgets) {
      widget.visible = terminal;
      widget.active = terminal;
    }
    for (AbstractWidget widget : inventoryWidgets) {
      widget.visible = !terminal;
      widget.active = !terminal;
    }
    if (terminal) {
      setInitialFocus(this.editor);
    }
  }

  private void inventoryAction(int action, int slot) {
    if (slot >= 0) {
      ClientPlayNetworking.send(new TurtleInventoryActionC2S(this.pos, action, slot));
    }
  }

  private void program(int action) {
    String name = this.nameField.getValue().trim();
    boolean needsName =
        action == ProgramActionC2S.SAVE
            || action == ProgramActionC2S.LOAD
            || action == ProgramActionC2S.DELETE;
    if (needsName && name.isEmpty()) {
      return;
    }
    String source = action == ProgramActionC2S.SAVE ? this.editor.getValue() : "";
    ClientPlayNetworking.send(new ProgramActionC2S(this.pos, action, name, source));
  }

  @Override
  public void removed() {
    if (open == this) {
      open = null;
    }
    super.removed();
  }

  private void runCurrent() {
    String program = this.editor.getValue();
    if (!program.isBlank()) {
      ClientPlayNetworking.send(new RunCommandC2S(this.pos, program));
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (tab == TAB_TERMINAL && event.button() == 0 && paneRowHeight > 0) {
      double mx = event.x();
      double my = event.y();
      if (mx >= MARGIN && mx <= MARGIN + PANE_W && my >= paneRowsTop) {
        int idx = (int) ((my - paneRowsTop) / paneRowHeight);
        if (idx >= 0 && idx < fileRows.size()) {
          FileRow row = fileRows.get(idx);
          if (row.path() != null) {
            this.nameField.setValue(row.path());
            program(ProgramActionC2S.LOAD);
            return true;
          }
        }
      }
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    boolean enter = event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
    boolean runModifier = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    if (tab == TAB_TERMINAL && enter && runModifier) {
      runCurrent();
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (tab == TAB_INVENTORY) {
      renderInventory(graphics);
    } else {
      renderTerminal(graphics);
    }
  }

  private void renderTerminal(GuiGraphicsExtractor graphics) {
    Font font = Minecraft.getInstance().font;
    int editorY = (this.editor != null ? this.editor.getY() : this.height);
    renderFilePane(graphics, font, editorY + EDITOR_HEIGHT);

    int left = MARGIN + PANE_W + PANE_GAP;
    int right = this.width - MARGIN;
    int top = CONTENT_TOP;
    int bottom = editorY - 6;
    int titleHeight = font.lineHeight + 6;

    graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, bottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(font, "MineSIer terminal", left + 6, top + 4, TITLE_COLOR);

    int lineHeight = font.lineHeight + 1;
    int textTop = top + titleHeight + 3;
    String[] lines = this.transcript.split("\n", -1);
    int maxLines = Math.max(1, (bottom - 4 - textTop) / lineHeight);
    int start = Math.max(0, lines.length - maxLines);
    int y = textTop;
    for (int i = start; i < lines.length; i++) {
      graphics.text(font, lines[i], left + 6, y, TEXT_COLOR);
      y += lineHeight;
    }
  }

  /** Draws the left file-tree pane (folders + clickable files) and records its row geometry. */
  private void renderFilePane(GuiGraphicsExtractor graphics, Font font, int paneBottom) {
    int left = MARGIN;
    int right = MARGIN + PANE_W;
    int top = CONTENT_TOP;
    int titleHeight = font.lineHeight + 6;

    graphics.fill(left - 1, top - 1, right + 1, paneBottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, paneBottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(font, "Files", left + 6, top + 4, TITLE_COLOR);

    int lineHeight = font.lineHeight + 1;
    int rowTop = top + titleHeight + 3;
    this.paneRowsTop = rowTop;
    this.paneRowHeight = lineHeight;

    if (fileRows.isEmpty()) {
      graphics.text(font, "(no programs)", left + 4, rowTop, TEXT_COLOR);
      return;
    }
    int maxChars = Math.max(1, (PANE_W - 8) / 6);
    int maxRows = Math.max(0, (paneBottom - 3 - rowTop) / lineHeight);
    for (int i = 0; i < fileRows.size() && i < maxRows; i++) {
      FileRow row = fileRows.get(i);
      String label = "  ".repeat(row.depth()) + row.label();
      if (label.length() > maxChars) {
        label = label.substring(0, maxChars);
      }
      int color = row.path() == null ? TITLE_COLOR : TEXT_COLOR;
      graphics.text(font, label, left + 4, rowTop + i * lineHeight, color);
    }
  }

  private void renderInventory(GuiGraphicsExtractor graphics) {
    Font font = Minecraft.getInstance().font;
    int left = MARGIN;
    int right = this.width - MARGIN;
    int top = CONTENT_TOP;
    int bottom = this.height - MARGIN;
    int titleHeight = font.lineHeight + 6;

    graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, bottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
    graphics.text(
        font,
        "Inventory  (selected slot " + (invSelected + 1) + ")",
        left + 6,
        top + 4,
        TITLE_COLOR);

    int gridLeft = left + 8;
    int gridTop = top + titleHeight + 4;
    int slotW = 64;
    int slotH = 36;
    for (int i = 0; i < 16; i++) {
      int x = gridLeft + (i % 4) * (slotW + 4);
      int y = gridTop + (i / 4) * slotH;
      boolean selected = i == invSelected;
      graphics.fill(
          x - 1, y - 1, x + slotW + 1, y + slotH - 2, selected ? SELECTED_COLOR : BORDER_COLOR);
      graphics.fill(x, y, x + slotW, y + slotH - 3, PANEL_COLOR);
      String content = i < invSlots.length && !invSlots[i].isBlank() ? invSlots[i] : "empty";
      if (content.length() > 10) content = content.substring(0, 10);
      graphics.text(font, content, x + 3, y + 23, selected ? SELECTED_COLOR : TEXT_COLOR);
    }
    if (invSlots.length == 0) {
      graphics.text(font, "Loading inventory...", left + 6, top + titleHeight + 4, TEXT_COLOR);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
