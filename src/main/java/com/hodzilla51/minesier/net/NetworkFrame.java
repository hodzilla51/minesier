package com.hodzilla51.minesier.net;

/** A layer-2 frame exposed to MineSIer programs. */
public record NetworkFrame(String source, String destination, String data) {
}
