package com.hodzilla51.minesier.net;

import java.util.ArrayDeque;
import java.util.Deque;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Tick-budgeted dispatcher for network receive handlers. */
public final class NetworkManager {
	private static final int MAX_QUEUED_EVENTS = 1_024;
	private static final int MAX_EVENTS_PER_TICK = 4;
	private static final Deque<Event> EVENTS = new ArrayDeque<>();

	private NetworkManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> dispatch());
	}

	/** Called on the server thread by a NIC. Returns false when backpressure drops the event. */
	public static boolean schedule(NetworkListener listener, NetworkFrame frame) {
		if (EVENTS.size() >= MAX_QUEUED_EVENTS) {
			return false;
		}
		EVENTS.addLast(new Event(listener, frame));
		return true;
	}

	private static void dispatch() {
		for (int processed = 0; processed < MAX_EVENTS_PER_TICK && !EVENTS.isEmpty(); processed++) {
			Event event = EVENTS.removeFirst();
			try {
				event.listener.onFrame(event.frame);
			} catch (Exception ignored) {
				// Script errors are reported through the owning computer's transcript.
			}
		}
	}

	private record Event(NetworkListener listener, NetworkFrame frame) {
	}
}
