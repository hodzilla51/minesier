package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.net.TurtleVisualAction;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Client-side store of in-progress turtle slide animations, keyed by destination position. Written
 * by the {@code TurtleMoveS2C} receiver (client thread) and read by the block entity renderer
 * (render thread) — hence concurrent. Timing is purely client-relative (the tick the packet
 * arrived), so it needs no server clock sync.
 */
public final class TurtleAnimations {
  public static final long EFFECT_TICKS = 12L;
  private static final int MAX_EFFECTS_PER_TURTLE = 12;

  public record Slide(Direction fromDir, long startTick, int durationTicks) {}

  /** A turn in progress: how many degrees to ease away from the (already-final) facing. */
  public record Turn(float deltaDeg, long startTick) {}

  /** A brief screen/status effect caused by a successful turtle world action. */
  public record Effect(TurtleVisualAction action, String detail, long startTick) {}

  private static final Map<BlockPos, Slide> SLIDES = new ConcurrentHashMap<>();
  private static final Map<BlockPos, Turn> TURNS = new ConcurrentHashMap<>();
  private static final Map<BlockPos, Deque<Effect>> EFFECTS = new ConcurrentHashMap<>();

  private TurtleAnimations() {}

  public static void begin(BlockPos pos, Direction fromDir, long clientTick, int durationTicks) {
    SLIDES.put(pos.immutable(), new Slide(fromDir, clientTick, Math.max(1, durationTicks)));
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

  public static void beginEffect(
      BlockPos pos, TurtleVisualAction action, String detail, long clientTick) {
    BlockPos key = pos.immutable();
    Deque<Effect> effects = EFFECTS.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
    synchronized (effects) {
      if (effects.size() >= MAX_EFFECTS_PER_TURTLE) {
        // Preserve the effect currently visible to the player; newer cosmetic events can be
        // dropped safely under a traffic burst.
        return;
      }
      Effect last = effects.peekLast();
      long startTick =
          last == null ? clientTick : Math.max(clientTick, last.startTick() + EFFECT_TICKS);
      effects.addLast(new Effect(action, detail, startTick));
    }
  }

  public static Effect getEffect(BlockPos pos, long clientTick) {
    Deque<Effect> effects = EFFECTS.get(pos);
    if (effects == null) {
      return null;
    }
    synchronized (effects) {
      while (!effects.isEmpty() && effects.peekFirst().startTick() + EFFECT_TICKS <= clientTick) {
        effects.removeFirst();
      }
      if (effects.isEmpty()) {
        EFFECTS.remove(pos, effects);
        return null;
      }
      return effects.peekFirst();
    }
  }
}
