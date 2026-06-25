package com.hodzilla51.minesier.turtle;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.block.TurtleAccess;
import com.hodzilla51.minesier.block.TurtleBlock;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-side registry of running turtle programs. Each is ticked once per server tick (so actions
 * take real time); state is decoupled from the block entity (which gets recreated as the turtle
 * hops) and written back to the BE at the final position when the program ends. All access is on
 * the server thread.
 */
public final class TurtleManager {
  private static final List<Running> ACTIVE = new ArrayList<>();

  private TurtleManager() {}

  /** Hooks the per-tick driver. Call once from common init. */
  public static void init() {
    ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());
  }

  /** Starts a program on the turtle at {@code pos}, unless one is already running there. */
  public static void run(Level level, BlockPos pos, ServerPlayer player, String command) {
    if (!(level.getBlockEntity(pos) instanceof TurtleBlockEntity be)) {
      return;
    }
    for (Running r : ACTIVE) {
      if (r.world.pos().equals(pos)) {
        ServerPlayNetworking.send(
            player,
            new TerminalScreenS2C(
                pos, be.getTranscript() + "\n[busy — wait for the current program]", false));
        return;
      }
    }

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
    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
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
          public String read(String path) {
            return be.readFile(path);
          }

          @Override
          public boolean write(String path, String text) {
            return be.saveFile(path, text);
          }

          @Override
          public boolean remove(String path) {
            return be.deleteFile(path);
          }

          @Override
          public boolean exists(String path) {
            return be.fileExists(path);
          }
        });
    TurtleAccess world =
        new TurtleAccess(
            level, pos, facing, be.getFuel(), inventory, equipment, be.getSelectedSlot(), network);
    TurtleBrain brain = new TurtleBrain(vm, world, command);

    List<String> base = new ArrayList<>(List.of(be.getTranscript().split("\n", -1)));
    base.addAll(echo(command));

    // Carry the inserted disk across any movement (its data rides on the item itself).
    ItemStack disk = be.getDisk();

    ACTIVE.add(new Running(brain, world, vm, player, level, base, disk));
    brain.start();
  }

  /**
   * True when the live inventory is owned by a running Turtle program rather than its block entity.
   */
  public static boolean isRunning(Level level, BlockPos pos) {
    return ACTIVE.stream().anyMatch(r -> r.level == level && r.world.pos().equals(pos));
  }

  private static void tickAll() {
    for (Iterator<Running> it = ACTIVE.iterator(); it.hasNext(); ) {
      Running r = it.next();
      r.brain.tick();
      flushOutput(r, true);
      r.ticks++;
      if (r.ticks > MineSIerConfig.maxTurtleProgramTicks && !r.brain.isFinished()) {
        r.brain.abort();
      }
      if (r.brain.isFinished() || r.brain.isAborted()) {
        finish(r);
        it.remove();
      }
    }
  }

  private static void finish(Running r) {
    BlockPos endPos = r.world.pos();
    int fuel = r.world.fuel();

    flushOutput(r, false);
    String transcript = String.join("\n", r.baseTranscript);

    // Facing already lives in the block's state (updated as the turtle turned/moved).
    if (r.level.getBlockEntity(endPos) instanceof TurtleBlockEntity end) {
      end.applyResult(
          r.vm,
          fuel,
          r.world.inventory(),
          r.world.equipment(),
          r.world.selectedSlot(),
          r.disk,
          transcript);
    }
    ServerPlayNetworking.send(r.player, new TerminalScreenS2C(endPos, transcript, false));
  }

  private static void flushOutput(Running r, boolean notifyClient) {
    List<String> out = r.brain.drainOutput();
    if (!out.isEmpty()) {
      r.baseTranscript.addAll(out);
      while (r.baseTranscript.size() > MineSIerConfig.maxTranscriptLines) {
        r.baseTranscript.remove(0);
      }
    }
    if (!out.isEmpty() && notifyClient) {
      ServerPlayNetworking.send(
          r.player,
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

  private static final class Running {
    final TurtleBrain brain;
    final TurtleAccess world;
    final JsComputer vm;
    final ServerPlayer player;
    final Level level;
    final List<String> baseTranscript;
    final ItemStack disk;
    int ticks;

    Running(
        TurtleBrain brain,
        TurtleAccess world,
        JsComputer vm,
        ServerPlayer player,
        Level level,
        List<String> baseTranscript,
        ItemStack disk) {
      this.brain = brain;
      this.world = world;
      this.vm = vm;
      this.player = player;
      this.level = level;
      this.baseTranscript = baseTranscript;
      this.disk = disk;
    }
  }
}
