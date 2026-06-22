package com.hodzilla51.minesier.block;

import java.util.ArrayList;
import java.util.List;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Backing state + VM for a placed Computer block.
 *
 * <p>Holds a scrollback transcript (input echoes + results) which is persisted
 * to NBT. The {@link JsComputer} (live scope) is transient, rebuilt on load —
 * variables defined in a previous session are intentionally not restored yet.
 */
public class ComputerBlockEntity extends BlockEntity implements ProgramStore {
	private static final String KEY_TRANSCRIPT = "Transcript";
	private static final String KEY_DISK = "Disk";
	private static final int MAX_LINES = 200;
	private static final String WELCOME = "MineSIer JS terminal — type an expression.";

	/** This computer's own sandboxed JS VM (1 block = 1 VM). */
	private final JsComputer computer = new JsComputer();

	private final List<String> transcript = new ArrayList<>(List.of(WELCOME));
	private ItemStack disk = ItemStack.EMPTY;

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.COMPUTER_BLOCK_ENTITY, pos, state);
	}

	/** Runs one program in this computer's VM, appending the echoed input + output to the transcript. */
	public void runCommand(String command) {
		String[] inputLines = command.split("\n", -1);
		transcript.add("> " + inputLines[0]);
		for (int i = 1; i < inputLines.length; i++) {
			transcript.add("  " + inputLines[i]); // continuation lines, indented
		}
		transcript.addAll(computer.run(command));
		trim();
		setChanged();
	}

	/** The full scrollback joined with newlines (for sending to the client). */
	@Override
	public String getTranscript() {
		return String.join("\n", transcript);
	}

	@Override
	public ItemStack getDisk() {
		return disk;
	}

	public void setDisk(ItemStack disk) {
		this.disk = disk;
		setChanged();
	}

	@Override
	public void markChanged() {
		setChanged();
	}

	private void trim() {
		while (transcript.size() > MAX_LINES) {
			transcript.remove(0);
		}
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		String saved = in.getStringOr(KEY_TRANSCRIPT, WELCOME);
		transcript.clear();
		for (String line : saved.split("\n", -1)) {
			transcript.add(line);
		}
		this.disk = in.read(KEY_DISK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString(KEY_TRANSCRIPT, getTranscript());
		if (!disk.isEmpty()) {
			out.store(KEY_DISK, ItemStack.CODEC, disk);
		}
	}
}
