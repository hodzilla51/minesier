package com.hodzilla51.minesier.block;

import java.util.ArrayList;
import java.util.List;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Persistent shell + state for a placed turtle: its VM (so variables survive),
 * scrollback transcript, facing, and fuel. While a program runs, the authoritative
 * live state lives in the {@code turtle.TurtleManager}'s brain (the block hops and
 * its block entity is recreated); the brain writes final state back here via
 * {@link #applyResult}.
 */
public class TurtleBlockEntity extends BlockEntity implements ProgramStore {
	private static final String KEY_TRANSCRIPT = "Transcript";
	private static final String KEY_FACING = "Facing";
	private static final String KEY_FUEL = "Fuel";
	private static final String KEY_SELECTED = "SelectedSlot";
	private static final String KEY_DISK = "Disk";
	private static final int INVENTORY_SIZE = 16;
	private static final int DEFAULT_FUEL = 1000;
	private static final String WELCOME = "MineSIer turtle — try turtle.forward()";

	private JsComputer vm = new JsComputer();
	private final List<String> transcript = new ArrayList<>(List.of(WELCOME));
	private Direction facing = Direction.NORTH;
	private int fuel = DEFAULT_FUEL;
	private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private int selectedSlot = 0;
	private ItemStack disk = ItemStack.EMPTY;

	public TurtleBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.TURTLE_BLOCK_ENTITY, pos, state);
	}

	public Direction getFacing() {
		return facing;
	}

	public void setFacing(Direction facing) {
		this.facing = facing;
		setChanged();
	}

	public int getFuel() {
		return fuel;
	}

	public JsComputer getVm() {
		return vm;
	}

	public NonNullList<ItemStack> getInventory() {
		return inventory;
	}

	public int getSelectedSlot() {
		return selectedSlot;
	}

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

	/** Lands a finished program's state onto this (the turtle's final-position) block entity. */
	public void applyResult(JsComputer vm, Direction facing, int fuel, NonNullList<ItemStack> inventory,
			int selectedSlot, ItemStack disk, String transcript) {
		this.vm = vm;
		this.facing = facing;
		this.fuel = fuel;
		this.inventory = inventory;
		this.selectedSlot = selectedSlot;
		this.disk = disk;
		this.transcript.clear();
		for (String line : transcript.split("\n", -1)) {
			this.transcript.add(line);
		}
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		String saved = in.getStringOr(KEY_TRANSCRIPT, WELCOME);
		transcript.clear();
		for (String line : saved.split("\n", -1)) {
			transcript.add(line);
		}
		this.facing = Direction.values()[in.getIntOr(KEY_FACING, Direction.NORTH.ordinal())];
		this.fuel = in.getIntOr(KEY_FUEL, DEFAULT_FUEL);
		this.selectedSlot = in.getIntOr(KEY_SELECTED, 0);
		this.inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(in, this.inventory);
		this.disk = in.read(KEY_DISK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString(KEY_TRANSCRIPT, getTranscript());
		out.putInt(KEY_FACING, facing.ordinal());
		out.putInt(KEY_FUEL, fuel);
		out.putInt(KEY_SELECTED, selectedSlot);
		ContainerHelper.saveAllItems(out, inventory);
		if (!disk.isEmpty()) {
			out.store(KEY_DISK, ItemStack.CODEC, disk);
		}
	}
}
