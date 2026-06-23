package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.net.NetworkFrame;

/** Narrow, Minecraft-free interface exposed to the {@code net} JavaScript global. */
public interface NetworkApi {
	String address();

	/** Injects one frame into the locally connected cable segment. */
	boolean send(String destination, String data);

	/** Returns the next frame accepted by this NIC, or {@code null} when none is queued. */
	NetworkFrame receive();
}
