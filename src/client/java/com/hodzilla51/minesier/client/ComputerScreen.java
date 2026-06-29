package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.net.OpenTurtleEquipmentC2S;
import com.hodzilla51.minesier.net.OpenTurtleInventoryC2S;
import com.hodzilla51.minesier.net.ProgramActionC2S;
import com.hodzilla51.minesier.net.RunCommandC2S;
import com.hodzilla51.minesier.net.StopProcessC2S;
import java.lang.reflect.Field;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The computer/turtle screen: 3-pane IDE layout with a file tree on the left, a code editor (with
 * line numbers, tab bar, and cursor status) in the center, and a terminal panel at the bottom.
 *
 * <p>New files are created inline: clicking "+" in the file pane activates an inline text input
 * drawn directly in the file tree (no popup dialog). Enter creates the file on the server and opens
 * it in a tab. Every tab always has a server path — there are no "Untitled" buffers.
 */
public class ComputerScreen extends Screen {
  // Layout constants
  private static final int MARGIN = 12;
  private static final int CONTENT_TOP = 22;
  private static final int PANE_W = 104;
  private static final int PANE_GAP = 6;
  private static final int TAB_H = 18;
  private static final int GUTTER_W = 24;
  private static final int STATUS_H = 10;
  private static final int TERMINAL_H = 80;
  private static final int BUTTON_H = 20;

  // Context menu (right-click on a file row)
  private static final int CTX_W = 72;
  private static final int CTX_ITEM_H = 14;

  // Rename dialog dimensions
  private static final int DLG_W = 164;
  private static final int DLG_H = 62;

  // Panel skin colors (ARGB)
  private static final int PANEL_COLOR    = 0xFF0C120C;
  private static final int BORDER_COLOR   = 0xFF3A6B3A;
  private static final int TITLEBAR_COLOR = 0xFF18301A;
  private static final int TITLE_COLOR    = 0xFF7CFC7C;
  private static final int TEXT_COLOR     = 0xFFD0E8D0;
  private static final int DIRTY_COLOR    = 0xFFFFAA44;
  private static final int INPUT_BG       = 0xFF1A2A1A;
  private static final int SELECTED_BG     = 0xFF24482A;

  private static ComputerScreen open;

  private BlockPos pos;
  private String transcript;
  private final boolean turtle;

  private MultiLineEditBox editor;
  private Field tfField = null;

  // ── Tabs ─────────────────────────────────────────────────────────────────

  /**
   * @param path          in-game file path (e.g. {@code startup.js}); never null
   * @param savedContent  content last confirmed by the server — dirty baseline
   * @param editorContent current editor buffer; updated on every keystroke
   */
  private record Tab(String path, String savedContent, String editorContent) {
    boolean dirty() { return !editorContent.equals(savedContent); }

    String displayName() {
      // Strip drive prefix (C:/ or D:/) then take the last segment.
      String p = (path.length() > 3 && path.charAt(1) == ':') ? path.substring(3) : path;
      int slash = p.lastIndexOf('/');
      return slash >= 0 ? p.substring(slash + 1) : p;
    }
  }

  private final List<Tab> openTabs = new ArrayList<>();
  private int activeTab = -1;
  /** Path requested from the server but not yet confirmed. */
  private String pendingLoadPath = null;

  // ── Process state ─────────────────────────────────────────────────────────
  private boolean isRunning = false;
  private String processName = "";
  private Button runButton = null;
  private Button stopButton = null;

  // ── File tree ─────────────────────────────────────────────────────────────
  private String[] programs = new String[0];
  private final List<FileRow> fileRows = new ArrayList<>();
  private int paneRowsTop;
  private int paneRowHeight;

  /** Full paths of collapsed folders (e.g. {@code "C:/"}, {@code "C:/lib/"}). */
  private final java.util.Set<String> collapsedFolders = new java.util.HashSet<>();
  /** The folder currently selected (its full path with trailing {@code /}), or null. */
  private String selectedFolder = null;

  /**
   * @param depth      indentation level
   * @param label      text to draw (folders/headers carry an expand marker)
   * @param path       file path for file rows; null for folders/headers
   * @param folderPath folder path (trailing {@code /}) for folder/header rows; null for files
   */
  private record FileRow(int depth, String label, String path, String folderPath) {}

  private static final class Dir {
    final TreeMap<String, Dir> dirs = new TreeMap<>();
    final TreeSet<String> files = new TreeSet<>();
  }

  // ── Inline new-file input ─────────────────────────────────────────────────
  /**
   * True while the user is typing a new filename inline in the file pane. Drawing is done in
   * {@link #renderFilePane}; keyboard is handled by overriding {@link #keyPressed} and {@link
   * #charTyped}. No MC widget is involved, which avoids widget-lifecycle timing issues.
   */
  private boolean newFileActive = false;
  private final StringBuilder newFileName = new StringBuilder();
  /** Target folder for the inline new-file input (trailing {@code /}), or null for C: root. */
  private String newFileDir = null;

  // ── Context menu ──────────────────────────────────────────────────────────
  private String contextMenuFile = null;
  private int contextMenuX, contextMenuY;

  // ── Rename dialog ─────────────────────────────────────────────────────────
  private String renameFromPath = null;
  private EditBox renameInputBox = null;
  private Button renameOkButton = null;
  private Button renameCancelButton = null;

  // ─────────────────────────────────────────────────────────────────────────

  public ComputerScreen(BlockPos pos, String transcript, boolean turtle) {
    super(Component.literal("Computer"));
    this.pos = pos;
    this.transcript = transcript;
    this.turtle = turtle;
  }

  public ComputerScreen(BlockPos pos, String transcript) {
    this(pos, transcript, false);
  }

  public static void updateIfOpen(BlockPos pos, String transcript) {
    if (open != null) {
      open.pos = pos;
      open.transcript = transcript;
    }
  }

  public static void loadIntoEditor(String source) {
    if (open == null) return;
    String path = open.pendingLoadPath;
    open.pendingLoadPath = null;
    if (path != null) {
      open.openOrFocusTab(path, source);
    } else if (open.editor != null) {
      open.editor.setValue(source);
    }
  }

  public static void setProcessState(boolean running, String name) {
    if (open == null) return;
    open.isRunning = running;
    open.processName = name;
    if (open.runButton != null) open.runButton.active = !running;
    if (open.stopButton != null) open.stopButton.active = running;
  }

  public static void showPrograms(BlockPos pos, String names) {
    if (open != null && open.pos.equals(pos)) {
      open.programs = names.isEmpty() ? new String[0] : names.split("\n", -1);
      open.rebuildFileRows();
    }
  }

  // ── init ─────────────────────────────────────────────────────────────────

  @Override
  protected void init() {
    Font font = Minecraft.getInstance().font;

    if (turtle) {
      addRenderableWidget(
          Button.builder(Component.literal("Inventory"), b -> openInventory())
              .bounds(MARGIN, 2, 80, 16).build());
      addRenderableWidget(
          Button.builder(Component.literal("Equipment"), b -> openEquipment())
              .bounds(MARGIN + 84, 2, 90, 16).build());
    }

    int contentLeft  = MARGIN + PANE_W + PANE_GAP;
    int buttonY      = this.height - MARGIN - BUTTON_H;
    int terminalY    = buttonY - 4 - TERMINAL_H;
    int statusY      = terminalY - 4 - STATUS_H;
    int editorTopY   = CONTENT_TOP + TAB_H;
    int editorLeft   = contentLeft + GUTTER_W;
    int editorWidth  = this.width - editorLeft - MARGIN;
    int editorHeight = Math.max(20, statusY - 4 - editorTopY);

    this.editor =
        MultiLineEditBox.builder()
            .setX(editorLeft)
            .setY(editorTopY)
            .setShowBackground(true)
            .setShowDecorations(true)
            .build(
                font, editorWidth, editorHeight,
                Component.literal("JS program — Ctrl/Cmd+Enter to run"));
    this.editor.setCharacterLimit(8192);
    this.editor.setValueListener(
        val -> {
          if (activeTab >= 0 && activeTab < openTabs.size()) {
            Tab t = openTabs.get(activeTab);
            openTabs.set(activeTab, new Tab(t.path(), t.savedContent(), val));
          }
        });
    addRenderableWidget(this.editor);

    // Bottom row: [Save] [Eject] [Stop] [Run]
    int gap    = 4;
    int bw     = 44;
    int runX   = this.width - MARGIN - bw;
    int stopX  = runX   - gap - bw;
    int ejectX = stopX  - gap - bw;
    int saveX  = ejectX - gap - bw;

    addRenderableWidget(
        Button.builder(Component.literal("Save"), b -> saveCurrentTab())
            .bounds(saveX, buttonY, bw, BUTTON_H).build());
    addRenderableWidget(
        Button.builder(Component.literal("Eject"), b -> eject())
            .bounds(ejectX, buttonY, bw, BUTTON_H).build());
    this.stopButton =
        Button.builder(Component.literal("Stop"), b -> stopCurrent())
            .bounds(stopX, buttonY, bw, BUTTON_H).build();
    this.stopButton.active = isRunning;
    addRenderableWidget(this.stopButton);
    this.runButton =
        Button.builder(Component.literal("Run"), b -> runCurrent())
            .bounds(runX, buttonY, bw, BUTTON_H).build();
    this.runButton.active = !isRunning;
    addRenderableWidget(this.runButton);

    setInitialFocus(this.editor);
    rebuildFileRows();
    open = this;
  }

  // ── Tab management ────────────────────────────────────────────────────────

  private void openOrFocusTab(String path, String content) {
    for (int i = 0; i < openTabs.size(); i++) {
      if (path.equals(openTabs.get(i).path())) {
        openTabs.set(i, new Tab(path, content, content));
        switchToTab(i);
        return;
      }
    }
    openTabs.add(new Tab(path, content, content));
    activeTab = openTabs.size() - 1;
    if (editor != null) editor.setValue(content);
  }

  private void switchToTab(int idx) {
    activeTab = idx;
    if (editor != null) editor.setValue(openTabs.get(idx).editorContent());
  }

  private void closeTab(int idx) {
    openTabs.remove(idx);
    if (activeTab > idx) activeTab--;
    else if (activeTab >= openTabs.size()) activeTab = openTabs.size() - 1;
    if (editor == null) return;
    editor.setValue(activeTab >= 0 ? openTabs.get(activeTab).editorContent() : "");
  }

  private int tabWidth(Tab t) {
    Font font = Minecraft.getInstance().font;
    String label = (t.dirty() ? "* " : "") + t.displayName();
    return Math.min(font.width(label) + 22, 120);
  }

  // ── File tree ─────────────────────────────────────────────────────────────

  private void rebuildFileRows() {
    fileRows.clear();
    // Group entries by drive letter. C: always exists; others appear only when present.
    TreeMap<String, Dir> drives = new TreeMap<>();
    drives.put("C:", new Dir());
    for (String name : programs) {
      if (name.isBlank()) continue;
      int colon = name.indexOf(':');
      String drive =
          (colon > 0 && colon + 1 < name.length() && name.charAt(colon + 1) == '/')
              ? name.substring(0, colon + 1).toUpperCase()
              : "C:";
      String relative = drive.length() + 1 <= name.length() ? name.substring(drive.length() + 1) : "";
      Dir root = drives.computeIfAbsent(drive, k -> new Dir());
      if (relative.isEmpty()) continue; // bare drive root — header only
      Dir node = root;
      // A trailing slash marks an (empty) directory; create the dir nodes but add no file.
      boolean isDir = relative.endsWith("/");
      String[] parts = relative.split("/");
      int fileParts = isDir ? parts.length : parts.length - 1;
      for (int i = 0; i < fileParts; i++) {
        if (parts[i].isEmpty()) continue;
        node = node.dirs.computeIfAbsent(parts[i], k -> new Dir());
      }
      if (!isDir) {
        node.files.add(parts[parts.length - 1]);
      }
    }
    // Render C: first, then the rest in alphabetical order (D:, N:, …).
    emitDrive("C:", drives.remove("C:"), true);
    for (var entry : drives.entrySet()) {
      emitDrive(entry.getKey(), entry.getValue(), false);
    }
  }

  /** Appends a drive header and (when expanded) its tree. {@code alwaysEmpty} shows "(empty)". */
  private void emitDrive(String drive, Dir root, boolean showEmptyNote) {
    String mount = drive + "/";
    boolean collapsed = collapsedFolders.contains(mount);
    fileRows.add(new FileRow(0, marker(collapsed) + "[" + drive + "]", null, mount));
    if (collapsed) return;
    flattenDir(root, mount, 1);
    if (showEmptyNote && root.dirs.isEmpty() && root.files.isEmpty()) {
      fileRows.add(new FileRow(1, "(empty)", null, null));
    }
  }

  /** Expand/collapse marker shown before folder labels. */
  private static String marker(boolean collapsed) {
    return collapsed ? "▶ " : "▼ "; // ▶ collapsed / ▼ expanded
  }

  private void flattenDir(Dir node, String prefix, int depth) {
    for (var entry : node.dirs.entrySet()) {
      String fp = prefix + entry.getKey() + "/";
      boolean collapsed = collapsedFolders.contains(fp);
      fileRows.add(new FileRow(depth, marker(collapsed) + entry.getKey() + "/", null, fp));
      if (!collapsed) flattenDir(entry.getValue(), fp, depth + 1);
    }
    for (String file : node.files) {
      fileRows.add(new FileRow(depth, file, prefix + file, null));
    }
  }

  // ── Inline new-file input ─────────────────────────────────────────────────

  private void beginNewFile() {
    newFileActive = true;
    newFileName.setLength(0);
    // Create inside the selected folder, if any. Expand it so the new entry is visible.
    newFileDir = selectedFolder;
    if (newFileDir != null) {
      collapsedFolders.remove(newFileDir);
      rebuildFileRows();
    }
    // Steal focus away from the editor so the editor doesn't consume charTyped events.
    setFocused(null);
  }

  private void confirmNewFile() {
    String name = newFileName.toString().trim();
    String dir = newFileDir;
    newFileActive = false;
    newFileName.setLength(0);
    newFileDir = null;
    setFocused(editor);
    if (name.isEmpty()) return;
    boolean hasDrive = name.length() > 2 && name.charAt(1) == ':';
    String fullPath;
    if (hasDrive) {
      fullPath = name;                 // explicit drive overrides the selected folder
    } else if (dir != null) {
      fullPath = dir + name;           // dir ends with "/"
    } else {
      fullPath = "C:/" + name;         // no folder selected → C: root
    }
    if (fullPath.endsWith("/")) {
      // Create directory — no tab opened.
      ClientPlayNetworking.send(new ProgramActionC2S(this.pos, ProgramActionC2S.MKDIR, fullPath, "", ""));
    } else {
      // Create empty file and open it in a tab.
      ClientPlayNetworking.send(new ProgramActionC2S(this.pos, ProgramActionC2S.SAVE, fullPath, "", ""));
      openOrFocusTab(fullPath, "");
    }
  }

  private void cancelNewFile() {
    newFileActive = false;
    newFileName.setLength(0);
    newFileDir = null;
    setFocused(editor);
  }

  /** Toggles a folder's collapsed state and rebuilds the row list. */
  private void toggleFolder(String folderPath) {
    if (!collapsedFolders.remove(folderPath)) {
      collapsedFolders.add(folderPath);
    }
    rebuildFileRows();
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private void openInventory() {
    ClientPlayNetworking.send(new OpenTurtleInventoryC2S(this.pos, this.width, this.height));
  }

  private void openEquipment() {
    ClientPlayNetworking.send(new OpenTurtleEquipmentC2S(this.pos, this.width, this.height));
  }

  private void loadFile(String path) {
    pendingLoadPath = path;
    ClientPlayNetworking.send(new ProgramActionC2S(this.pos, ProgramActionC2S.LOAD, path, "", ""));
  }

  /** Re-requests the file tree from the server (the server replies with ProgramListS2C). */
  private void reloadFiles() {
    ClientPlayNetworking.send(new ProgramActionC2S(this.pos, ProgramActionC2S.LIST, "", "", ""));
  }

  private void saveCurrentTab() {
    if (activeTab < 0 || activeTab >= openTabs.size()) return;
    Tab t = openTabs.get(activeTab);
    String content = t.editorContent();
    ClientPlayNetworking.send(new ProgramActionC2S(this.pos, ProgramActionC2S.SAVE, t.path(), content, ""));
    openTabs.set(activeTab, new Tab(t.path(), content, content));
  }

  private void eject() {
    ClientPlayNetworking.send(new ProgramActionC2S(this.pos, ProgramActionC2S.EJECT, "", "", ""));
  }

  private void runCurrent() {
    if (editor != null && !editor.getValue().isBlank()) {
      ClientPlayNetworking.send(new RunCommandC2S(this.pos, editor.getValue()));
    }
  }

  private void stopCurrent() {
    ClientPlayNetworking.send(new StopProcessC2S(this.pos));
  }

  @Override
  public void removed() {
    if (open == this) open = null;
    cancelNewFile();
    closeRenameDialog();
    super.removed();
  }

  // ── Input handling ────────────────────────────────────────────────────────

  @Override
  public boolean charTyped(CharacterEvent event) {
    // When the inline new-file input is active, accumulate printable characters.
    if (newFileActive) {
      if (event.isAllowedChatCharacter() && newFileName.length() < 96) {
        newFileName.appendCodePoint(event.codepoint());
      }
      return true;
    }
    return super.charTyped(event);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    int mx = (int) event.x();
    int my = (int) event.y();

    // Rename dialog intercepts all clicks; clicking outside dismisses it.
    if (renameFromPath != null) {
      int dlgX = (this.width - DLG_W) / 2;
      int dlgY = (this.height - DLG_H) / 2;
      if (mx < dlgX || mx > dlgX + DLG_W || my < dlgY || my > dlgY + DLG_H) {
        closeRenameDialog();
        return true;
      }
      return super.mouseClicked(event, doubleClick);
    }

    // Clicking outside the file pane cancels the new-file input.
    if (newFileActive && (mx < MARGIN || mx > MARGIN + PANE_W)) {
      cancelNewFile();
      // fall through so the click is also processed normally
    }

    // Context menu: dispatch or dismiss.
    if (contextMenuFile != null) {
      int cmH = 3 * CTX_ITEM_H + 4;
      if (mx >= contextMenuX && mx <= contextMenuX + CTX_W
          && my >= contextMenuY && my <= contextMenuY + cmH) {
        int idx = (my - contextMenuY - 2) / CTX_ITEM_H;
        if (idx >= 0 && idx < 3) {
          String file = contextMenuFile;
          contextMenuFile = null;
          dispatchContextAction(idx, file);
          return true;
        }
      }
      contextMenuFile = null;
    }

    if (event.button() == 0) {
      Font font = Minecraft.getInstance().font;

      // Tab bar clicks (right side only)
      if (my >= CONTENT_TOP && my < CONTENT_TOP + TAB_H && mx >= MARGIN + PANE_W + PANE_GAP) {
        int left = MARGIN + PANE_W + PANE_GAP;
        int x = left;
        for (int i = 0; i < openTabs.size(); i++) {
          int w = tabWidth(openTabs.get(i));
          if (mx >= x && mx < x + w) {
            if (mx >= x + w - 14) closeTab(i);
            else switchToTab(i);
            return true;
          }
          x += w + 2;
        }
      }

      // File pane title bar buttons: "↻" reload and "+" new file
      if (my >= CONTENT_TOP && my < CONTENT_TOP + font.lineHeight + 6) {
        if (mx >= MARGIN + PANE_W - 14 && mx <= MARGIN + PANE_W) {
          beginNewFile();
          return true;
        }
        if (mx >= MARGIN + PANE_W - 28 && mx < MARGIN + PANE_W - 14) {
          reloadFiles();
          return true;
        }
      }

      // Left-click on a row: open a file, or toggle+select a folder (skip while input active)
      if (!newFileActive && paneRowHeight > 0
          && mx >= MARGIN && mx <= MARGIN + PANE_W && my >= paneRowsTop) {
        int idx = (my - paneRowsTop) / paneRowHeight;
        if (idx >= 0 && idx < fileRows.size()) {
          FileRow row = fileRows.get(idx);
          if (row.path() != null) {
            loadFile(row.path());
            return true;
          }
          if (row.folderPath() != null) {
            selectedFolder = row.folderPath();
            toggleFolder(row.folderPath());
            return true;
          }
        }
      }
    }

    // Right-click on a file row: context menu
    if (event.button() == 1 && !newFileActive && paneRowHeight > 0
        && mx >= MARGIN && mx <= MARGIN + PANE_W && my >= paneRowsTop) {
      int idx = (my - paneRowsTop) / paneRowHeight;
      if (idx >= 0 && idx < fileRows.size() && fileRows.get(idx).path() != null) {
        contextMenuFile = fileRows.get(idx).path();
        contextMenuX = mx;
        contextMenuY = my;
        return true;
      }
    }

    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    // New-file inline input consumes all keyboard events while active.
    if (newFileActive) {
      if (event.key() == GLFW.GLFW_KEY_ESCAPE) { cancelNewFile(); return true; }
      boolean enter = event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
      if (enter) { confirmNewFile(); return true; }
      if (event.key() == GLFW.GLFW_KEY_BACKSPACE && newFileName.length() > 0) {
        newFileName.deleteCharAt(newFileName.length() - 1);
        return true;
      }
      return true; // swallow all other keys
    }

    if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
      if (contextMenuFile != null) { contextMenuFile = null; return true; }
      if (renameFromPath != null) { closeRenameDialog(); return true; }
    }
    boolean enter = event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
    if (renameFromPath != null && enter) { confirmRename(); return true; }
    boolean mod = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    if (mod && event.key() == GLFW.GLFW_KEY_S) { saveCurrentTab(); return true; }
    if (mod && enter) { runCurrent(); return true; }
    return super.keyPressed(event);
  }

  // ── Context menu ──────────────────────────────────────────────────────────

  private void dispatchContextAction(int itemIdx, String path) {
    switch (itemIdx) {
      case 0 -> loadFile(path);
      case 1 -> openRenameDialog(path);
      case 2 -> {
        ClientPlayNetworking.send(
            new ProgramActionC2S(this.pos, ProgramActionC2S.DELETE, path, "", ""));
        for (int i = openTabs.size() - 1; i >= 0; i--) {
          if (path.equals(openTabs.get(i).path())) { closeTab(i); break; }
        }
      }
    }
  }

  // ── Rename dialog ─────────────────────────────────────────────────────────

  private void openRenameDialog(String fromPath) {
    closeRenameDialog();
    this.renameFromPath = fromPath;
    Font font = Minecraft.getInstance().font;
    int dlgX = (this.width - DLG_W) / 2;
    int dlgY = (this.height - DLG_H) / 2;
    this.renameInputBox =
        new EditBox(font, dlgX + 4, dlgY + 20, DLG_W - 8, 16, Component.literal("path"));
    this.renameInputBox.setMaxLength(96);
    this.renameInputBox.setValue(fromPath);
    addRenderableWidget(this.renameInputBox);
    this.renameOkButton =
        Button.builder(Component.literal("OK"), b -> confirmRename())
            .bounds(dlgX + 4, dlgY + 42, 72, 14).build();
    addRenderableWidget(this.renameOkButton);
    this.renameCancelButton =
        Button.builder(Component.literal("Cancel"), b -> closeRenameDialog())
            .bounds(dlgX + 84, dlgY + 42, 76, 14).build();
    addRenderableWidget(this.renameCancelButton);
    setFocused(this.renameInputBox);
  }

  private void closeRenameDialog() {
    if (renameInputBox    != null) { removeWidget(renameInputBox);    renameInputBox    = null; }
    if (renameOkButton    != null) { removeWidget(renameOkButton);    renameOkButton    = null; }
    if (renameCancelButton != null) { removeWidget(renameCancelButton); renameCancelButton = null; }
    renameFromPath = null;
  }

  private void confirmRename() {
    if (renameInputBox == null) return;
    String newPath = renameInputBox.getValue().trim();
    String oldPath = renameFromPath;
    closeRenameDialog();
    if (newPath.isEmpty() || oldPath == null || newPath.equals(oldPath)) return;
    ClientPlayNetworking.send(
        new ProgramActionC2S(this.pos, ProgramActionC2S.RENAME, oldPath, "", newPath));
    for (int i = 0; i < openTabs.size(); i++) {
      if (oldPath.equals(openTabs.get(i).path())) {
        Tab t = openTabs.get(i);
        openTabs.set(i, new Tab(newPath, t.savedContent(), t.editorContent()));
        break;
      }
    }
  }

  // ── Rendering ─────────────────────────────────────────────────────────────

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    renderTabBar(graphics);
    renderGutter(graphics);
    renderStatusBar(graphics);
    renderTerminal(graphics);
    if (contextMenuFile != null) renderContextMenu(graphics);
    if (renameFromPath != null) renderRenameDialog(graphics);
  }

  private void renderTabBar(GuiGraphicsExtractor graphics) {
    Font font = Minecraft.getInstance().font;
    int left  = MARGIN + PANE_W + PANE_GAP;
    int right = this.width - MARGIN;
    int top   = CONTENT_TOP;

    graphics.fill(left, top, right, top + TAB_H, TITLEBAR_COLOR);
    graphics.fill(left, top + TAB_H - 1, right, top + TAB_H, BORDER_COLOR);

    int x = left;
    for (int i = 0; i < openTabs.size(); i++) {
      Tab t = openTabs.get(i);
      boolean active = (i == activeTab);
      int w  = tabWidth(t);
      int bg = active ? PANEL_COLOR : TITLEBAR_COLOR;
      int fg = t.dirty() ? DIRTY_COLOR : TEXT_COLOR;
      graphics.fill(x, top, x + w, top + TAB_H - (active ? 0 : 1), bg);
      if (active) {
        graphics.fill(x, top, x + 1, top + TAB_H, BORDER_COLOR);
        graphics.fill(x + w - 1, top, x + w, top + TAB_H, BORDER_COLOR);
        graphics.fill(x, top, x + w, top + 1, BORDER_COLOR);
      }
      String label = (t.dirty() ? "* " : "") + t.displayName();
      graphics.text(font, label, x + 4, top + (TAB_H - font.lineHeight) / 2, fg);
      graphics.text(font, "×", x + w - 12, top + (TAB_H - font.lineHeight) / 2, TEXT_COLOR);
      x += w + 2;
    }
  }

  private void renderGutter(GuiGraphicsExtractor graphics) {
    if (editor == null) return;
    Font font    = Minecraft.getInstance().font;
    int left     = MARGIN + PANE_W + PANE_GAP;
    int top      = editor.getY();
    int bottom   = top + editor.getHeight();
    int lineH    = font.lineHeight + 1;
    int innerPad = 4;

    graphics.fill(left, top, left + GUTTER_W, bottom, TITLEBAR_COLOR);
    graphics.fill(left + GUTTER_W - 1, top, left + GUTTER_W, bottom, BORDER_COLOR);

    double scroll  = editor.scrollAmount();
    int firstLine  = (int) (scroll / lineH);
    int offsetPx   = (int) (scroll % lineH);
    int lineY      = top + innerPad - offsetPx;

    for (int i = firstLine; lineY < bottom - innerPad + lineH; i++, lineY += lineH) {
      if (lineY >= top + innerPad - lineH && lineY < bottom - innerPad + lineH) {
        String num = String.valueOf(i + 1);
        int numX = left + GUTTER_W - font.width(num) - 4;
        graphics.text(font, num, numX, lineY, TEXT_COLOR);
      }
    }
  }

  private void renderStatusBar(GuiGraphicsExtractor graphics) {
    if (editor == null) return;
    Font font     = Minecraft.getInstance().font;
    int left      = MARGIN + PANE_W + PANE_GAP;
    int right     = this.width - MARGIN;
    int buttonY   = this.height - MARGIN - BUTTON_H;
    int terminalY = buttonY - 4 - TERMINAL_H;
    int statusY   = terminalY - 4 - STATUS_H;

    graphics.fill(left, statusY, right, statusY + STATUS_H, TITLEBAR_COLOR);
    graphics.fill(left, statusY, right, statusY + 1, BORDER_COLOR);

    int textY = statusY + (STATUS_H - font.lineHeight) / 2;
    if (isRunning) {
      String label = "● " + (processName.isEmpty() ? "running" : processName);
      graphics.text(font, label, left + 4, textY, DIRTY_COLOR);
    }
    String cursorPos = getCursorPosition();
    if (!cursorPos.isEmpty()) {
      graphics.text(font, cursorPos, right - font.width(cursorPos) - 4, textY, TEXT_COLOR);
    }
  }

  private String getCursorPosition() {
    if (editor == null) return "";
    try {
      if (tfField == null) {
        tfField = MultiLineEditBox.class.getDeclaredField("textField");
        tfField.setAccessible(true);
      }
      Object tf = tfField.get(editor);
      int cursorOffset = (int) tf.getClass().getMethod("cursor").invoke(tf);
      String value = editor.getValue();
      String before = value.substring(0, Math.min(cursorOffset, value.length()));
      int line = before.split("\n", -1).length;
      int col  = before.length() - (before.lastIndexOf('\n') + 1);
      return "Ln " + line + ", Col " + (col + 1);
    } catch (Exception ignored) {
      return "";
    }
  }

  private void renderTerminal(GuiGraphicsExtractor graphics) {
    Font font     = Minecraft.getInstance().font;
    int buttonY   = this.height - MARGIN - BUTTON_H;
    int terminalY = buttonY - 4 - TERMINAL_H;
    renderFilePane(graphics, font, terminalY + TERMINAL_H);

    int left   = MARGIN + PANE_W + PANE_GAP;
    int right  = this.width - MARGIN;
    int top    = terminalY;
    int bottom = terminalY + TERMINAL_H;
    int titleH = font.lineHeight + 6;

    graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, bottom, PANEL_COLOR);
    graphics.fill(left, top, right, top + titleH, TITLEBAR_COLOR);
    graphics.text(font, "MineSIer terminal", left + 6, top + 4, TITLE_COLOR);

    int lineH   = font.lineHeight + 1;
    int textTop = top + titleH + 3;
    String[] lines = this.transcript.split("\n", -1);
    int maxLines = Math.max(1, (bottom - 4 - textTop) / lineH);
    int start = Math.max(0, lines.length - maxLines);
    int y = textTop;
    for (int i = start; i < lines.length; i++) {
      graphics.text(font, lines[i], left + 6, y, TEXT_COLOR);
      y += lineH;
    }
  }

  private void renderFilePane(GuiGraphicsExtractor graphics, Font font, int paneBottom) {
    int left   = MARGIN;
    int right  = MARGIN + PANE_W;
    int top    = CONTENT_TOP;
    int titleH = font.lineHeight + 6;

    // Outer border + background
    graphics.fill(left - 1, top - 1, right + 1, paneBottom + 1, BORDER_COLOR);
    graphics.fill(left, top, right, paneBottom, PANEL_COLOR);

    // Title bar
    graphics.fill(left, top, right, top + titleH, TITLEBAR_COLOR);
    graphics.text(font, "Files", left + 6, top + 3, TITLE_COLOR);
    // "↻" reload button + "+" new-file button (highlighted when input is active)
    graphics.text(font, "↻", right - 24, top + 3, TITLE_COLOR);
    int plusColor = newFileActive ? DIRTY_COLOR : TITLE_COLOR;
    graphics.text(font, "+", right - 10, top + 3, plusColor);

    int lineH  = font.lineHeight + 1;
    int rowTop = top + titleH + 2;
    this.paneRowsTop   = rowTop;
    this.paneRowHeight = lineH;

    int maxChars = Math.max(1, (PANE_W - 8) / 6);
    int boxH     = lineH + 2;

    // Where the new-file input box goes: inside the selected folder (right after its row),
    // otherwise at the end of the C: section (just before the next drive header, if any).
    int insertIndex = fileRows.size();
    for (int i = 1; i < fileRows.size(); i++) {
      FileRow r = fileRows.get(i);
      if (r.depth() == 0 && r.folderPath() != null && !"C:/".equals(r.folderPath())) {
        insertIndex = i;
        break;
      }
    }
    int inputIndent = 1;
    if (newFileActive && newFileDir != null) {
      for (int i = 0; i < fileRows.size(); i++) {
        if (newFileDir.equals(fileRows.get(i).folderPath())) {
          insertIndex = i + 1;
          inputIndent = folderDepth(newFileDir) + 1;
          break;
        }
      }
    }

    // ── Draw rows row-by-row, inserting the input box at insertIndex ──
    int y = rowTop;
    for (int i = 0; i < fileRows.size(); i++) {
      if (y >= paneBottom - 3) break;

      if (newFileActive && i == insertIndex) {
        drawInputBox(graphics, font, left, right, y, boxH, inputIndent);
        y += boxH + 1;
        if (y >= paneBottom - 3) break;
      }

      FileRow row  = fileRows.get(i);
      // Highlight the currently selected folder.
      if (row.folderPath() != null && row.folderPath().equals(selectedFolder)) {
        graphics.fill(left + 1, y - 1, right - 1, y + lineH - 1, SELECTED_BG);
      }
      String label = "  ".repeat(row.depth()) + row.label();
      if (label.length() > maxChars) label = label.substring(0, maxChars);
      int color = row.path() == null ? TITLE_COLOR : TEXT_COLOR;
      graphics.text(font, label, left + 4, y, color);
      y += lineH;
    }

    // Input box when insertIndex is past the last row (e.g. no D:, no folder selected).
    if (newFileActive && insertIndex >= fileRows.size() && y < paneBottom - 3) {
      drawInputBox(graphics, font, left, right, y, boxH, inputIndent);
    }
  }

  /** Number of path segments below the drive (e.g. {@code "C:/lib/sub/"} → 2, {@code "C:/"} → 0). */
  private static int folderDepth(String folderPath) {
    if (folderPath == null || folderPath.length() <= 3) return 0;
    int segs = 0;
    for (String s : folderPath.substring(3).split("/")) {
      if (!s.isEmpty()) segs++;
    }
    return segs;
  }

  private void drawInputBox(
      GuiGraphicsExtractor graphics, Font font, int left, int right, int y, int boxH, int indent) {
    graphics.fill(left + 1, y, right - 1, y + boxH, INPUT_BG);
    graphics.fill(left + 1, y, right - 1, y + 1, BORDER_COLOR);
    graphics.fill(left + 1, y + boxH - 1, right - 1, y + boxH, BORDER_COLOR);
    graphics.text(font, "  ".repeat(indent) + newFileName + "|", left + 4, y + 1, TEXT_COLOR);
  }

  private void renderContextMenu(GuiGraphicsExtractor graphics) {
    Font font = Minecraft.getInstance().font;
    int cmH = 3 * CTX_ITEM_H + 4;
    graphics.fill(
        contextMenuX - 1, contextMenuY - 1,
        contextMenuX + CTX_W + 1, contextMenuY + cmH + 1, BORDER_COLOR);
    graphics.fill(
        contextMenuX, contextMenuY,
        contextMenuX + CTX_W, contextMenuY + cmH, PANEL_COLOR);
    String[] items = {"Open", "Rename", "Delete"};
    for (int i = 0; i < items.length; i++) {
      graphics.text(font, items[i], contextMenuX + 4, contextMenuY + 2 + i * CTX_ITEM_H, TEXT_COLOR);
    }
  }

  private void renderRenameDialog(GuiGraphicsExtractor graphics) {
    Font font = Minecraft.getInstance().font;
    int dlgX  = (this.width - DLG_W) / 2;
    int dlgY  = (this.height - DLG_H) / 2;
    graphics.fill(dlgX - 1, dlgY - 1, dlgX + DLG_W + 1, dlgY + DLG_H + 1, BORDER_COLOR);
    graphics.fill(dlgX, dlgY, dlgX + DLG_W, dlgY + DLG_H, PANEL_COLOR);
    graphics.fill(dlgX, dlgY, dlgX + DLG_W, dlgY + font.lineHeight + 6, TITLEBAR_COLOR);
    graphics.text(font, "Rename: " + renameFromPath, dlgX + 4, dlgY + 4, TITLE_COLOR);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
