package com.hodzilla51.minesier.net;

/** A registered program callback for frames accepted by one NIC. */
@FunctionalInterface
public interface NetworkListener {
	void onFrame(NetworkFrame frame);
}
