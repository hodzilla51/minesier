package com.hodzilla51.minesier.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Client-side store of in-progress turtle slide animations, keyed by destination
 * position. Written by the {@code TurtleMoveS2C} receiver (client thread) and read
 * by the block entity renderer (render thread) — hence concurrent. Timing is purely
 * client-relative (the tick the packet arrived), so it needs no server clock sync.
 */
public final class TurtleAnimations {
	public record Slide(Direction fromDir, long startTick) {
	}

	/** A turn in progress: how many degrees to ease away from the (already-final) facing. */
	public record Turn(float deltaDeg, long startTick) {
	}

	private static final Map<BlockPos, Slide> SLIDES = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Turn> TURNS = new ConcurrentHashMap<>();

	private TurtleAnimations() {
	}

	public static void begin(BlockPos pos, Direction fromDir, long clientTick) {
		SLIDES.put(pos.immutable(), new Slide(fromDir, clientTick));
	}

	public static Slide get(BlockPos pos) {
		return SLIDES.get(pos);
	}

	/** Records a turn at {@code pos}: {@code deltaDeg} = +90 (right) or -90 (left). */
	public static void beginTurn(BlockPos pos, float deltaDeg, long clientTick) {
		TURNS.put(pos.immutable(), new Turn(deltaDeg, clientTick));
	}

	public static Turn getTurn(BlockPos pos) {
		return TURNS.get(pos);
	}
}
