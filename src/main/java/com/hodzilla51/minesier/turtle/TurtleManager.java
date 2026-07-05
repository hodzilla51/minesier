package com.hodzilla51.minesier.turtle;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.block.TurtleAccess;
import com.hodzilla51.minesier.block.TurtleBlock;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.js.ProcessName;
import com.hodzilla51.minesier.net.ProcessStateS2C;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-side registry of running turtle programs. Each is ticked once per server tick (so actions
 * take real time); state is decoupled from the block entity (which gets recreated as the turtle
 * hops) and written back to the BE at the final position when the program ends. All access is on
 * the server thread.
 *
 * <p>A program that leaves timers or receive listeners becomes a resident daemon: it stays in
 * {@link #ACTIVE} serving callbacks on its runner. The daemon's restart recipe (its source) is
 * flushed onto the block entity every tick, so a reload/chunk-load re-runs it (see {@link
 * TurtleBlockEntity#serverTick}).
 */
public final class TurtleManager {
  private static final List<Running> ACTIVE = new ArrayList<>();

  /**
   * Launches queued while an existing resident daemon at the same position is being folded. A new
   * top-level command can't reuse the VM until the old runner has fully exited, so it waits here
   * and starts once {@link #findAt} reports the slot free (see {@link #processPending}).
   */
  private static final List<PendingStart> PENDING = new ArrayList<>();

  private TurtleManager() {}

  /** Hooks the per-tick driver and the fold-on-unload / fold-on-stop handlers. Call once. */
  public static void init() {
    ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());
    ServerChunkEvents.CHUNK_UNLOAD.register(
        (level, chunk) -> foldInChunk(level, chunk.getPos().getWorldPosition()));
    ServerLifecycleEvents.SERVER_STOPPING.register(server -> foldAll());
  }

  /**
   * Runs a top-level command on the turtle at {@code pos} (the Run control). If a resident daemon
   * is idling there it is folded and replaced; if a program is actively executing the command is
   * refused.
   */
  public static void run(Level level, BlockPos pos, ServerPlayer player, String command) {
    launch(level, pos, player, command, echo(command));
  }

  /**
   * Boots a program from a source (a disk {@code startup.js}); replaces an idle daemon, refuses if
   * busy — same policy as {@link #run}.
   */
  public static void startProgram(
      ServerLevel level, BlockPos pos, ServerPlayer viewer, String source, String note) {
    launch(level, pos, viewer, source, List.of(note));
  }

  /**
   * Re-runs a persisted daemon's source after a reload/chunk-load. Only reached when nothing is
   * running at {@code pos} (the caller guards on {@link #isRunning}), so it never has to replace.
   */
  public static void restartResident(ServerLevel level, BlockPos pos, String source) {
    launch(level, pos, null, source, List.of("[resident program restarted]"));
  }

  /**
   * Shared launch path. Refuses when a JS job is actively executing (busy); folds an idle resident
   * daemon and queues the new launch until its runner exits; otherwise starts immediately.
   */
  private static void launch(
      Level level, BlockPos pos, ServerPlayer viewer, String source, List<String> echoLines) {
    if (!(level.getBlockEntity(pos) instanceof TurtleBlockEntity be)) {
      return;
    }
    Running existing = findAt(level, pos);
    if (existing != null) {
      if (existing.vm.isBusy()) {
        if (viewer != null) {
          ServerPlayNetworking.send(
              viewer,
              new TerminalScreenS2C(
                  pos, be.getTranscript() + "\n[busy — wait for the current program]", false));
        }
        return;
      }
      // Idle resident daemon: fold it, then start the replacement once its runner has exited.
      existing.vm.stopAllHandles();
      existing.brain.abort();
      PENDING.add(new PendingStart(level, pos, viewer, source, echoLines));
      return;
    }
    start(level, pos, be, viewer, source, echoLines);
  }

  /** Sets up the VM, brain, and live state, registers the Running, and starts the runner. */
  private static void start(
      Level level,
      BlockPos pos,
      TurtleBlockEntity be,
      ServerPlayer viewer,
      String source,
      List<String> echoLines) {
    JsComputer vm = be.getVm();
    // Work on a copy of the inventory; the final state is written back on completion.
    NonNullList<ItemStack> inventory =
        NonNullList.withSize(be.getInventory().size(), ItemStack.EMPTY);
    for (int i = 0; i < inventory.size(); i++) {
      inventory.set(i, be.getInventory().get(i).copy());
    }
    NonNullList<ItemStack> equipment =
        NonNullList.withSize(TurtleBlockEntity.EQUIPMENT_SIZE, ItemStack.EMPTY);
    for (int i = 0; i < equipment.size(); i++) {
      equipment.set(i, be.getEquipment().get(i).copy());
    }
    Direction facing = level.getBlockState(pos).getValue(TurtleBlock.FACING);
    TurtleNetworkState network = be.getNetwork();
    if (level instanceof ServerLevel serverLevel) {
      network.attach(serverLevel, pos, facing);
    }
    vm.setNetwork(network);
    vm.setModuleLoader(be::loadProgram);
    vm.setFileSystem(
        new com.hodzilla51.minesier.js.FileSystemApi() {
          @Override
          public List<String> list(String path) {
            return be.listFiles(path);
          }

          @Override
          public byte[] readBytes(String path) {
            return be.readFileBytes(path);
          }

          @Override
          public boolean writeBytes(String path, byte[] data) {
            return be.saveFileBytes(path, data);
          }

          @Override
          public boolean remove(String path) {
            return be.deleteFile(path);
          }

          @Override
          public boolean exists(String path) {
            return be.fileExists(path);
          }

          @Override
          public boolean mount(
              String drive, com.hodzilla51.minesier.disk.FileSystemProvider provider) {
            String letter = com.hodzilla51.minesier.block.ProgramStore.canonicalDrive(drive);
            if (letter == null || provider == null) return false;
            be.dynamicMounts().put(letter, provider);
            return true;
          }

          @Override
          public boolean unmount(String drive) {
            String letter = com.hodzilla51.minesier.block.ProgramStore.canonicalDrive(drive);
            return letter != null && be.dynamicMounts().remove(letter) != null;
          }

          @Override
          public List<String> mounts() {
            return new java.util.ArrayList<>(be.mounts().keySet());
          }
        });
    TurtleAccess world =
        new TurtleAccess(
            level, pos, facing, be.getFuel(), inventory, equipment, be.getSelectedSlot(), network);
    TurtleBrain brain = new TurtleBrain(vm, world, source);

    List<String> base = new ArrayList<>(List.of(be.getTranscript().split("\n", -1)));
    base.addAll(echoLines);

    // Carry the inserted disk across any movement (its data rides on the item itself).
    ItemStack disk = be.getDisk();

    ACTIVE.add(
        new Running(
            brain,
            world,
            vm,
            viewer,
            level,
            base,
            disk,
            be.accessMode(),
            be.passwordSalt(),
            be.passwordHash(),
            source));
    brain.start();
  }

  /**
   * True when the live inventory is owned by a running Turtle program rather than its block entity.
   */
  public static boolean isRunning(Level level, BlockPos pos) {
    return findAt(level, pos) != null;
  }

  /** The name of the process running at {@code pos}, or an empty string when nothing runs there. */
  public static String processNameAt(Level level, BlockPos pos) {
    Running r = findAt(level, pos);
    return r == null ? "" : ProcessName.describe(r.source);
  }

  /**
   * Points the running program at {@code pos} to a new terminal viewer (last opener wins), and
   * returns its current live transcript — or null when nothing runs there.
   */
  public static String attachViewer(Level level, BlockPos pos, ServerPlayer viewer) {
    Running r = findAt(level, pos);
    if (r == null) {
      return null;
    }
    r.viewer = viewer;
    return String.join("\n", r.baseTranscript);
  }

  private static Running findAt(Level level, BlockPos pos) {
    for (Running r : ACTIVE) {
      if (r.level == level && r.world.pos().equals(pos)) {
        return r;
      }
    }
    return null;
  }

  private static void tickAll() {
    for (Iterator<Running> it = ACTIVE.iterator(); it.hasNext(); ) {
      Running r = it.next();
      r.brain.tick();
      // A resident daemon may have requested its own stop via proc.stop() from inside a callback;
      // honor it on the server thread (never tear the VM down mid-callback).
      if (r.vm.consumeStopRequest()) {
        r.vm.stopAllHandles();
        r.brain.abort();
      }
      // Drive resident timers once the foreground program is done (so they start counting from the
      // resident phase, like a computer's after run()): advance countdowns on the server thread (no
      // JS) and hand any that fell due to the runner thread, where the callback executes paced by
      // tick() like any turtle op.
      if (r.brain.isForegroundDone()) {
        for (Object due : r.vm.collectDueTimers()) {
          r.brain.enqueue(() -> r.vm.fireTimer(due));
        }
      }
      flushOutput(r, true);
      // Flush dup-critical live state (fuel/inventory/equipment/slot) onto the current-position BE
      // every tick, so an autosave or crash mid-program can't desync persisted state from the
      // turtle's already-persisted physical moves (which would duplicate fuel/items on reload).
      // The resident-source recipe rides along: empty until the program is actually resident, so a
      // crash mid-foreground never replays a one-shot program on reload.
      if (r.level.getBlockEntity(r.world.pos()) instanceof TurtleBlockEntity cur) {
        cur.syncLiveState(
            r.world.fuel(),
            r.world.inventory(),
            r.world.equipment(),
            r.world.selectedSlot(),
            r.vm.isResident() ? r.source : "");
      }
      r.ticks++;
      if (!r.brain.isForegroundDone()) {
        // The program-tick budget bounds the FOREGROUND program only.
        if (r.ticks > MineSIerConfig.maxTurtleProgramTicks && !r.brain.isFinished()) {
          r.brain.abort();
        }
      } else if (r.vm.isBusy()) {
        // A resident daemon runs indefinitely, but a single callback that never returns (e.g. an
        // unbounded movement loop) must not run forever: bound one continuous callback execution.
        if (++r.busyTicks > MineSIerConfig.maxTurtleCallbackTicks && !r.brain.isFinished()) {
          r.baseTranscript.add("[callback exceeded tick budget — stopped]");
          r.vm.stopAllHandles();
          r.brain.abort();
        }
      } else {
        r.busyTicks = 0;
      }
      // Remove only once the runner thread has truly exited (isFinished), so a replacement launch
      // never starts a new worker on a VM the old worker is still unwinding.
      if (r.brain.isFinished()) {
        finish(r);
        it.remove();
      }
    }
    processPending();
  }

  /** Starts any launch waiting for its predecessor's runner to exit; drops timed-out ones. */
  private static void processPending() {
    if (PENDING.isEmpty()) {
      return;
    }
    for (Iterator<PendingStart> it = PENDING.iterator(); it.hasNext(); ) {
      PendingStart p = it.next();
      if (findAt(p.level, p.pos) == null) {
        it.remove();
        if (p.level.getBlockEntity(p.pos) instanceof TurtleBlockEntity be) {
          start(p.level, p.pos, be, p.viewer, p.source, p.echoLines);
          // The folded predecessor's finish() just told the viewer "stopped"; correct it — the
          // replacement is now running (same tick, so this send wins on the client).
          if (p.viewer != null) {
            ServerPlayNetworking.send(
                p.viewer, new ProcessStateS2C(true, ProcessName.describe(p.source)));
          }
        }
      } else if (--p.ttl <= 0) {
        it.remove();
        if (p.viewer != null && p.level.getBlockEntity(p.pos) instanceof TurtleBlockEntity be) {
          ServerPlayNetworking.send(
              p.viewer,
              new TerminalScreenS2C(p.pos, be.getTranscript() + "\n[replace timed out]", false));
        }
      }
    }
  }

  /**
   * Stops the program or resident daemon running on the turtle at {@code pos} (the Stop control).
   */
  public static void stop(Level level, BlockPos pos) {
    Running r = findAt(level, pos);
    if (r != null) {
      r.vm.stopAllHandles(); // drop timers/listeners so the runner's resident loop exits
      r.brain.abort(); // wake + unwind the worker; finish() writes back once it exits
    }
  }

  /** Folds every daemon whose turtle sits in the unloading chunk, persisting its restart recipe. */
  private static void foldInChunk(ServerLevel level, BlockPos chunkOrigin) {
    int chunkX = chunkOrigin.getX() >> 4;
    int chunkZ = chunkOrigin.getZ() >> 4;
    for (Running r : new ArrayList<>(ACTIVE)) {
      if (r.level == level
          && (r.world.pos().getX() >> 4) == chunkX
          && (r.world.pos().getZ() >> 4) == chunkZ) {
        fold(r);
      }
    }
    PENDING.removeIf(
        p -> p.level == level && (p.pos.getX() >> 4) == chunkX && (p.pos.getZ() >> 4) == chunkZ);
  }

  /** Folds every running turtle (server is stopping); resident recipes persist for next load. */
  private static void foldAll() {
    for (Running r : new ArrayList<>(ACTIVE)) {
      fold(r);
    }
    PENDING.clear();
  }

  /**
   * Suspends a running turtle: a resident daemon's source is persisted (so the next load restarts
   * it) while a mid-flight one-shot program is simply dropped. Writes back synchronously because
   * the block entity is still reachable at fold time (before the chunk actually unloads).
   */
  private static void fold(Running r) {
    r.keepResidentOnFinish = r.vm.isResident();
    r.vm.stopAllHandles();
    r.brain.abort();
    // Unload/shutdown: persist state only. The player's connection may be closing, so don't try to
    // push terminal/process packets (a failed send must never abort the persistence loop).
    r.viewer = null;
    finish(r);
    ACTIVE.remove(r);
  }

  private static void finish(Running r) {
    BlockPos endPos = r.world.pos();
    int fuel = r.world.fuel();

    flushOutput(r, false);
    String transcript = String.join("\n", r.baseTranscript);

    // Keep the restart recipe only if a daemon is still resident (normal end) or we're folding a
    // resident daemon for unload; a stopped/replaced/one-shot program leaves nothing to restart.
    String residentSource = (r.keepResidentOnFinish || r.vm.isResident()) ? r.source : "";

    // Facing already lives in the block's state (updated as the turtle turned/moved).
    if (r.level.getBlockEntity(endPos) instanceof TurtleBlockEntity end) {
      end.applyResult(
          r.vm,
          fuel,
          r.world.inventory(),
          r.world.equipment(),
          r.world.selectedSlot(),
          r.disk,
          transcript,
          r.accessMode,
          r.passwordSalt,
          r.passwordHash,
          residentSource);
    }
    if (r.viewer != null) {
      ServerPlayNetworking.send(r.viewer, new TerminalScreenS2C(endPos, transcript, false));
      ServerPlayNetworking.send(
          r.viewer, new ProcessStateS2C(r.vm.isResident(), ProcessName.describe(r.source)));
    }
  }

  private static void flushOutput(Running r, boolean notifyClient) {
    List<String> out = r.brain.drainOutput();
    if (!out.isEmpty()) {
      r.baseTranscript.addAll(out);
      while (r.baseTranscript.size() > MineSIerConfig.maxTranscriptLines) {
        r.baseTranscript.remove(0);
      }
    }
    if (!out.isEmpty() && notifyClient && r.viewer != null) {
      ServerPlayNetworking.send(
          r.viewer,
          new TerminalScreenS2C(r.world.pos(), String.join("\n", r.baseTranscript), false));
    }
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

  /** A launch waiting for an idle daemon at the same position to finish folding. */
  private static final class PendingStart {
    final Level level;
    final BlockPos pos;
    final ServerPlayer viewer;
    final String source;
    final List<String> echoLines;
    int ttl = 100; // ticks before giving up if the folded runner never reports finished

    PendingStart(
        Level level, BlockPos pos, ServerPlayer viewer, String source, List<String> echoLines) {
      this.level = level;
      this.pos = pos;
      this.viewer = viewer;
      this.source = source;
      this.echoLines = echoLines;
    }
  }

  private static final class Running {
    final TurtleBrain brain;
    final TurtleAccess world;
    final JsComputer vm;
    ServerPlayer viewer;
    final Level level;
    final List<String> baseTranscript;
    final ItemStack disk;
    final String accessMode;
    final String passwordSalt;
    final String passwordHash;
    final String source;
    boolean keepResidentOnFinish;
    int ticks;
    int busyTicks;

    Running(
        TurtleBrain brain,
        TurtleAccess world,
        JsComputer vm,
        ServerPlayer viewer,
        Level level,
        List<String> baseTranscript,
        ItemStack disk,
        String accessMode,
        String passwordSalt,
        String passwordHash,
        String source) {
      this.brain = brain;
      this.world = world;
      this.vm = vm;
      this.viewer = viewer;
      this.level = level;
      this.baseTranscript = baseTranscript;
      this.disk = disk;
      this.accessMode = accessMode;
      this.passwordSalt = passwordSalt;
      this.passwordHash = passwordHash;
      this.source = source;
    }
  }
}
