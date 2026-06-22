package com.hodzilla51.minesier.turtle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hodzilla51.minesier.block.TurtleAccess;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.net.TerminalScreenS2C;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-side registry of running turtle programs. Each is ticked once per server
 * tick (so actions take real time); state is decoupled from the block entity (which
 * gets recreated as the turtle hops) and written back to the BE at the final position
 * when the program ends. All access is on the server thread.
 */
public final class TurtleManager {
	private static final int MAX_LINES = 200;
	private static final int MAX_PROGRAM_TICKS = 12_000; // ~10 min safety cap

	private static final List<Running> ACTIVE = new ArrayList<>();

	private TurtleManager() {
	}

	/** Hooks the per-tick driver. Call once from common init. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());
	}

	/** Starts a program on the turtle at {@code pos}, unless one is already running there. */
	public static void run(Level level, BlockPos pos, ServerPlayer player, String command) {
		if (!(level.getBlockEntity(pos) instanceof TurtleBlockEntity be)) {
			return;
		}
		for (Running r : ACTIVE) {
			if (r.world.pos().equals(pos)) {
				ServerPlayNetworking.send(player,
					new TerminalScreenS2C(pos, be.getTranscript() + "\n[busy — wait for the current program]", false));
				return;
			}
		}

		JsComputer vm = be.getVm();
		// Work on a copy of the inventory; the final state is written back on completion.
		NonNullList<ItemStack> inventory = NonNullList.withSize(be.getInventory().size(), ItemStack.EMPTY);
		for (int i = 0; i < inventory.size(); i++) {
			inventory.set(i, be.getInventory().get(i).copy());
		}
		TurtleAccess world = new TurtleAccess(level, pos, be.getFacing(), be.getFuel(), inventory, be.getSelectedSlot());
		TurtleBrain brain = new TurtleBrain(vm, world, command);

		List<String> base = new ArrayList<>(List.of(be.getTranscript().split("\n", -1)));
		base.addAll(echo(command));

		// Carry the inserted disk across any movement (its data rides on the item itself).
		ItemStack disk = be.getDisk();

		ACTIVE.add(new Running(brain, world, vm, player, level, base, disk));
		brain.start();
	}

	private static void tickAll() {
		for (Iterator<Running> it = ACTIVE.iterator(); it.hasNext();) {
			Running r = it.next();
			r.brain.tick();
			r.ticks++;
			if (r.ticks > MAX_PROGRAM_TICKS && !r.brain.isFinished()) {
				r.brain.abort();
			}
			if (r.brain.isFinished() || r.brain.isAborted()) {
				finish(r);
				it.remove();
			}
		}
	}

	private static void finish(Running r) {
		BlockPos endPos = r.world.pos();
		Direction facing = r.world.facing();
		int fuel = r.world.fuel();

		List<String> lines = new ArrayList<>(r.baseTranscript);
		lines.addAll(r.brain.drainOutput());
		while (lines.size() > MAX_LINES) {
			lines.remove(0);
		}
		String transcript = String.join("\n", lines);

		if (r.level.getBlockEntity(endPos) instanceof TurtleBlockEntity end) {
			end.applyResult(r.vm, facing, fuel, r.world.inventory(), r.world.selectedSlot(), r.disk, transcript);
		}
		ServerPlayNetworking.send(r.player, new TerminalScreenS2C(endPos, transcript, false));
	}

	private static List<String> echo(String command) {
		List<String> lines = new ArrayList<>();
		String[] inputLines = command.split("\n", -1);
		lines.add("> " + inputLines[0]);
		for (int i = 1; i < inputLines.length; i++) {
			lines.add("  " + inputLines[i]);
		}
		return lines;
	}

	private static final class Running {
		final TurtleBrain brain;
		final TurtleAccess world;
		final JsComputer vm;
		final ServerPlayer player;
		final Level level;
		final List<String> baseTranscript;
		final ItemStack disk;
		int ticks;

		Running(TurtleBrain brain, TurtleAccess world, JsComputer vm, ServerPlayer player, Level level,
				List<String> baseTranscript, ItemStack disk) {
			this.brain = brain;
			this.world = world;
			this.vm = vm;
			this.player = player;
			this.level = level;
			this.baseTranscript = baseTranscript;
			this.disk = disk;
		}
	}
}
