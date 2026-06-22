package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.net.ProgramActionC2S;
import com.hodzilla51.minesier.net.RunCommandC2S;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The computer/turtle terminal: a scrollback (drawn as monospace lines) on top, a
 * multi-line {@link MultiLineEditBox} program editor at the bottom, and a Run button.
 * Ctrl/Cmd+Enter also runs. The whole program (newlines intact) is shipped to the
 * server, which runs it in the block's VM and echoes the transcript back.
 */
public class ComputerScreen extends Screen {
	private static final int MARGIN = 12;
	private static final int EDITOR_HEIGHT = 72;
	private static final int BUTTON_H = 20;

	// Terminal "CRT" skin colors (ARGB).
	private static final int PANEL_COLOR = 0xFF0C120C;
	private static final int BORDER_COLOR = 0xFF3A6B3A;
	private static final int TITLEBAR_COLOR = 0xFF18301A;
	private static final int TITLE_COLOR = 0xFF7CFC7C;
	private static final int TEXT_COLOR = 0xFFD0E8D0;

	/**
	 * The currently open terminal, if any. {@link Minecraft} no longer exposes the
	 * active screen, so we track it here to route server-pushed transcript updates.
	 */
	private static ComputerScreen open;

	private BlockPos pos;
	private String transcript;
	private MultiLineEditBox editor;
	private EditBox nameField;

	public ComputerScreen(BlockPos pos, String transcript) {
		super(Component.literal("Computer"));
		this.pos = pos;
		this.transcript = transcript;
	}

	/**
	 * Applies a server transcript update to the open terminal, re-keying its target
	 * position so the terminal follows a turtle that hopped to a new block.
	 */
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

	@Override
	protected void init() {
		Font font = Minecraft.getInstance().font;
		int editorWidth = this.width - MARGIN * 2;
		int buttonY = this.height - MARGIN - BUTTON_H;
		int editorY = buttonY - 4 - EDITOR_HEIGHT;

		this.editor = MultiLineEditBox.builder()
			.setX(MARGIN)
			.setY(editorY)
			.setShowBackground(true)
			.setShowDecorations(true)
			.build(font, editorWidth, EDITOR_HEIGHT, Component.literal("JS program — Ctrl/Cmd+Enter to run"));
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

		addRenderableWidget(Button.builder(Component.literal("Save"), b -> program(ProgramActionC2S.SAVE))
			.bounds(saveX, buttonY, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.literal("Load"), b -> program(ProgramActionC2S.LOAD))
			.bounds(loadX, buttonY, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.literal("List"), b -> program(ProgramActionC2S.LIST))
			.bounds(listX, buttonY, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.literal("Eject"), b -> program(ProgramActionC2S.EJECT))
			.bounds(ejectX, buttonY, bw, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.literal("Run"), b -> runCurrent())
			.bounds(runX, buttonY, bw, BUTTON_H).build());

		setInitialFocus(this.editor);
		open = this;
	}

	private void program(int action) {
		String name = this.nameField.getValue().trim();
		boolean needsName = action == ProgramActionC2S.SAVE || action == ProgramActionC2S.LOAD
			|| action == ProgramActionC2S.DELETE;
		if (needsName && name.isEmpty()) {
			return; // save/load/delete need a name
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
	public boolean keyPressed(KeyEvent event) {
		boolean enter = event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER;
		boolean runModifier = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
		if (enter && runModifier) {
			runCurrent();
			return true;
		}
		// Plain Enter falls through to the editor (inserts a newline).
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Font font = Minecraft.getInstance().font;

		int left = MARGIN;
		int right = this.width - MARGIN;
		int top = MARGIN;
		int bottom = (this.editor != null ? this.editor.getY() : this.height) - 6;
		int titleHeight = font.lineHeight + 6;

		// Framed terminal panel: border, body, then a title bar.
		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_COLOR);
		graphics.fill(left, top, right, bottom, PANEL_COLOR);
		graphics.fill(left, top, right, top + titleHeight, TITLEBAR_COLOR);
		graphics.text(font, "MineSIer terminal", left + 6, top + 4, TITLE_COLOR);

		// Scrollback: draw the tail that fits inside the panel, below the title bar.
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
