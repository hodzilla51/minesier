package com.hodzilla51.minesier.turtle;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.js.TurtleApi;
import com.hodzilla51.minesier.net.TurtleVisualAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tick-paced execution engine for one turtle program.
 *
 * <p>The JS program runs on a dedicated worker thread. Each {@code turtle.*} call hands an action
 * to the server thread and BLOCKS until {@link #tick()} (driven by the server tick loop) performs
 * it — so movement/dig/place take real time and the program pauses between them, instead of
 * teleporting through everything in one tick.
 *
 * <p>Thread safety: the worker thread only touches the synchronized handoff and its own JS context;
 * ALL world mutation happens on the server thread inside {@link #tick()}. The supplied {@link
 * TurtleApi} {@code world} is therefore only ever called from there.
 */
public final class TurtleBrain {
  /** Ticks a movement/dig/place action takes (also the slide-animation duration). */
  public static final int PACE_TICKS = 14;

  /** Ticks a turn takes — shorter than a move, but visible (not instant). */
  public static final int TURN_TICKS = 9;

  private static final Map<String, Integer> PACE =
      Map.of(
          "forward",
          PACE_TICKS,
          "back",
          PACE_TICKS,
          "up",
          PACE_TICKS,
          "down",
          PACE_TICKS,
          "dig",
          PACE_TICKS,
          "place",
          PACE_TICKS,
          "placeSelected",
          PACE_TICKS,
          "turnLeft",
          TURN_TICKS,
          "turnRight",
          TURN_TICKS);

  private final JsComputer vm;
  private final TurtleApi world;
  private final String program;
  private final List<String> output = new ArrayList<>();

  private final Object lock = new Object();
  private String op;
  private Object[] args;
  private boolean reqPending;
  private boolean resultReady;
  private boolean executing;
  private int ticksLeft;
  private int actionTicksTotal;
  private Object result;
  private volatile boolean aborted;
  private volatile boolean finished;
  private int streamedLines;
  private Thread worker;

  public TurtleBrain(JsComputer vm, TurtleApi world, String program) {
    this.vm = vm;
    this.world = world;
    this.program = program;
  }

  public boolean isFinished() {
    return finished;
  }

  public boolean isAborted() {
    return aborted;
  }

  public List<String> drainOutput() {
    synchronized (lock) {
      List<String> lines = new ArrayList<>(output);
      output.clear();
      return lines;
    }
  }

  /** Starts the worker thread running the program against the blocking proxy. */
  public void start() {
    vm.setTurtle(proxy);
    vm.setOutputListener(this::captureOutput);
    worker = new Thread(this::runProgram, "minesier-turtle");
    worker.setDaemon(true);
    worker.start();
  }

  private void captureOutput(String line) {
    synchronized (lock) {
      output.add(line);
      streamedLines++;
      lock.notifyAll();
    }
  }

  private void runProgram() {
    try {
      List<String> out = vm.run(program);
      if (out.stream().anyMatch(line -> line.startsWith("error:"))) {
        world.visual(TurtleVisualAction.ERROR, "!");
      }
      synchronized (lock) {
        int tailStart = Math.min(streamedLines, out.size());
        output.addAll(out.subList(tailStart, out.size()));
        finished = true;
        lock.notifyAll();
      }
    } finally {
      vm.setOutputListener(null);
    }
  }

  /** Stops the program; the worker's next (or current) action throws and unwinds. */
  public void abort() {
    aborted = true;
    synchronized (lock) {
      lock.notifyAll();
    }
    if (worker != null) {
      worker.interrupt();
    }
  }

  /**
   * Server thread, once per tick: advances the pending action, performing it when its pacing
   * elapses.
   */
  public void tick() {
    synchronized (lock) {
      if (aborted) {
        if (executing) {
          world.clearActionProgress(op, args);
        }
        return;
      }
      if (finished || !reqPending) {
        return;
      }
      if (!executing) {
        executing = true;
        ticksLeft = PACE.getOrDefault(op, 0);
        if ("wait".equals(op)) {
          ticksLeft = Math.clamp((int) args[0], 0, MineSIerConfig.maxTurtleWaitTicks);
        } else {
          ticksLeft = world.actionTicks(op, args, ticksLeft);
        }
        actionTicksTotal = ticksLeft;
        if (performAtStart(op)) {
          // Movement/turn effects happen at the start so the client can animate from the old
          // state toward the already-authoritative server state.
          result = perform(op, args);
        }
      }
      if (ticksLeft > 0) {
        if (!performAtStart(op)) {
          if (!world.actionStillValid(op, args)) {
            result = Boolean.FALSE;
            world.clearActionProgress(op, args);
            reqPending = false;
            executing = false;
            resultReady = true;
            lock.notifyAll();
            return;
          }
          world.actionProgress(op, args, actionTicksTotal - ticksLeft, actionTicksTotal);
        }
        ticksLeft--;
        return;
      }
      if (!performAtStart(op)) {
        result = perform(op, args);
        world.clearActionProgress(op, args);
      }
      reqPending = false;
      executing = false;
      resultReady = true;
      lock.notifyAll();
    }
  }

  private boolean performAtStart(String op) {
    return switch (op) {
      case "forward", "back", "up", "down", "turnLeft", "turnRight" -> true;
      default -> false;
    };
  }

  /** Runs on the server thread — the only place the world is touched. */
  private Object perform(String op, Object[] args) {
    return switch (op) {
      case "forward" -> world.forward();
      case "back" -> world.back();
      case "up" -> world.up();
      case "down" -> world.down();
      case "turnLeft" -> world.turnLeft();
      case "turnRight" -> world.turnRight();
      case "dig" -> world.dig();
      case "place" -> world.place((String) args[0]);
      case "placeSelected" -> world.placeSelected();
      case "wait" -> {
        world.waitTicks((int) args[0]);
        yield null;
      }
      case "select" -> {
        world.select((int) args[0]);
        yield null;
      }
      case "getSelectedSlot" -> world.getSelectedSlot();
      case "getItemCount" -> world.getItemCount((int) args[0]);
      case "detect" -> world.detect();
      case "inspect" -> world.inspect();
      case "getFuelLevel" -> world.getFuelLevel();
      case "scan" -> world.scan();
      case "refuel" -> {
        world.refuel((int) args[0]);
        yield null;
      }
      default -> null;
    };
  }

  /** The blocking, worker-thread-facing turtle API handed to the VM. */
  private final TurtleApi proxy =
      new TurtleApi() {
        @Override
        public boolean forward() {
          return (Boolean) call("forward");
        }

        @Override
        public boolean back() {
          return (Boolean) call("back");
        }

        @Override
        public boolean up() {
          return (Boolean) call("up");
        }

        @Override
        public boolean down() {
          return (Boolean) call("down");
        }

        @Override
        public boolean turnLeft() {
          return (Boolean) call("turnLeft");
        }

        @Override
        public boolean turnRight() {
          return (Boolean) call("turnRight");
        }

        @Override
        public boolean dig() {
          return (Boolean) call("dig");
        }

        @Override
        public boolean place(String blockId) {
          return (Boolean) call("place", blockId);
        }

        @Override
        public boolean placeSelected() {
          return (Boolean) call("placeSelected");
        }

        @Override
        public void waitTicks(int ticks) {
          call("wait", Math.max(0, ticks));
        }

        @Override
        public void visual(TurtleVisualAction action, String detail) {
          world.visual(action, detail);
        }

        @Override
        public void select(int slot) {
          call("select", slot);
        }

        @Override
        public int getSelectedSlot() {
          return (Integer) call("getSelectedSlot");
        }

        @Override
        public int getItemCount(int slot) {
          return (Integer) call("getItemCount", slot);
        }

        @Override
        public boolean detect() {
          return (Boolean) call("detect");
        }

        @Override
        public String inspect() {
          return (String) call("inspect");
        }

        @Override
        public int getFuelLevel() {
          return (Integer) call("getFuelLevel");
        }

        @Override
        public void refuel(int amount) {
          call("refuel", amount);
        }

        @Override
        public java.util.List<ScanResult> scan() {
          @SuppressWarnings("unchecked")
          java.util.List<ScanResult> results = (java.util.List<ScanResult>) call("scan");
          return results;
        }
      };

  /** Worker thread: submit an action and block until the server tick performs it. */
  private Object call(String op, Object... args) {
    synchronized (lock) {
      if (aborted) {
        throw new IllegalStateException("turtle stopped");
      }
      this.op = op;
      this.args = args;
      this.reqPending = true;
      this.resultReady = false;
      lock.notifyAll();
      while (!resultReady && !aborted) {
        try {
          lock.wait(2000);
        } catch (InterruptedException e) {
          aborted = true;
          Thread.currentThread().interrupt();
        }
      }
      if (aborted) {
        throw new IllegalStateException("turtle stopped");
      }
      return result;
    }
  }
}
