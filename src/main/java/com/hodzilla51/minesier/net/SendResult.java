package com.hodzilla51.minesier.net;

/**
 * Outcome of injecting a frame into the medium, surfaced to JS as a tri-state so a program can tell
 * a permanent failure apart from transient congestion:
 *
 * <ul>
 *   <li>{@link #DELIVERED} → JS {@code true}: the frame was offered to the cable segment (or
 *       wireless cell).
 *   <li>{@link #REJECTED} → JS {@code false}: permanent failure — no cable/modem on that face, a
 *       blank destination, an oversized frame, or a frame that hit the hop limit. Retrying it
 *       unchanged will not help.
 *   <li>{@link #CONGESTED} → JS {@code null}: the segment's per-tick bandwidth budget ({@link
 *       com.hodzilla51.minesier.MineSIerConfig#segmentFramesPerTick}) is exhausted, so the frame was
 *       dropped by the medium. Retrying on a later tick may succeed; splitting the segment with a
 *       switch raises the aggregate budget.
 * </ul>
 */
public enum SendResult {
  DELIVERED,
  REJECTED,
  CONGESTED
}
