package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.net.OpenTurtleInventoryC2S;
import com.hodzilla51.minesier.net.ProgramActionC2S;
import com.hodzilla51.minesier.net.RunCommandC2S;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * The computer/turtle screen: the JS terminal (scrollback + multi-line editor + program buttons),
 * plus a left file-tree pane. For turtles, an "Inventory" button opens the separate vanilla storage
 * menu ({@link TurtleScreen}) rather than cramming items into the terminal.
 */
public class ComputerScreen extends Screen {
  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22; // leaves room for the tab row
  private static final int EDITOR_HEIGHT = 72;
  private static final int BUTTON_H = 20;
  private static final int PANE_W = 104; // left file-tree pane width
  private static final int PANE_GAP = 6;

  // Panel skin colors (ARGB).
  private static final int PANEL_COLOR = 0xFF0C120C;
  private static final int BORDER_COLOR = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR = 0xFF7CFC7C;
  private static final int TEXT_COLOR = 0xFFD0E8D0;

  /** The currently open screen, if any (Minecraft no longer exposes the active screen). */
  private static ComputerScreen open;

  private BlockPos pos;
  private String transcript;
  private final boolean turtle;

  private MultiLineEditBox editor;
  private EditBox nameField;

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

  @Override
  protected void init() {
    Font font = Minecraft.getInstance().font;

    // Tab row: the terminal is always present; turtles get an Inventory button that opens the
    // separate vanilla storage menu.
    if (turtle) {
      addRenderableWidget(
          Button.builder(Component.literal("Inventory"), b -> openInventory())
              .bounds(MARGIN, 2, 80, 16)
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

    addRenderableWidget(
        Button.builder(Component.literal("Save"), b -> program(ProgramActionC2S.SAVE))
            .bounds(saveX, buttonY, bw, BUTTON_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Load"), b -> program(ProgramActionC2S.LOAD))
            .bounds(loadX, buttonY, bw, BUTTON_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("List"), b -> program(ProgramActionC2S.LIST))
            .bounds(listX, buttonY, bw, BUTTON_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Eject"), b -> program(ProgramActionC2S.EJECT))
            .bounds(ejectX, buttonY, bw, BUTTON_H)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Run"), b -> runCurrent())
            .bounds(runX, buttonY, bw, BUTTON_H)
            .build());

    setInitialFocus(this.editor);
    open = this;
  }

  /** Asks the server to open the turtle's vanilla storage menu (replaces this screen). */
  private void openInventory() {
    ClientPlayNetworking.send(new OpenTurtleInventoryC2S(this.pos, this.width, this.height));
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
    if (event.button() == 0 && paneRowHeight > 0) {
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
    if (enter && runModifier) {
      runCurrent();
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    renderTerminal(graphics);
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

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
