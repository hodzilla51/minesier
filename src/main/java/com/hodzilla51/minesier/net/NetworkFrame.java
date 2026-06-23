package com.hodzilla51.minesier.net;

/**
 * A layer-2 frame exposed to MineSIer programs.
 *
 * <p>{@code hops} counts how many times the frame has been forwarded by a switch
 * (built-in or player-built). It is the loop guard: a frame that exceeds
 * {@link #MAX_HOPS} forwards is dropped, so a miswired cable loop can't multiply
 * a frame forever into a broadcast storm.
 */
public record NetworkFrame(String source, String destination, String data, int hops) {
	/** Max forwards before a frame is dropped (loop guard). */
	public static final int MAX_HOPS = 16;

	/** A freshly originated frame (zero hops). */
	public NetworkFrame(String source, String destination, String data) {
		this(source, destination, data, 0);
	}

	/** A copy advanced one forward, or {@code null} if it would exceed {@link #MAX_HOPS}. */
	public NetworkFrame nextHop() {
		return hops + 1 >= MAX_HOPS ? null : new NetworkFrame(source, destination, data, hops + 1);
	}
}
