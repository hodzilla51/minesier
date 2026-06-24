package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.ProgramStore;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.turtle.TurtleManager;
import java.util.TreeSet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Registers MineSIer's payload types (both sides) and the server-side receiver. */
public final class MineSIerNet {
  /** How close (squared, blocks) a player must be to drive a terminal. */
  private static final double REACH_SQR = 8 * 8;

  private MineSIerNet() {}

  /** Must run on BOTH client and server (common init) so the codecs are known. */
  public static void registerPayloads() {
    PayloadTypeRegistry.serverboundPlay().register(RunCommandC2S.TYPE, RunCommandC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(ProgramActionC2S.TYPE, ProgramActionC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(RequestInventoryC2S.TYPE, RequestInventoryC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(TurtleInventoryActionC2S.TYPE, TurtleInventoryActionC2S.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TerminalScreenS2C.TYPE, TerminalScreenS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TurtleMoveS2C.TYPE, TurtleMoveS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TurtleTurnS2C.TYPE, TurtleTurnS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TurtleVisualS2C.TYPE, TurtleVisualS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(LoadProgramS2C.TYPE, LoadProgramS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(InventoryS2C.TYPE, InventoryS2C.CODEC);
  }

  /** Server-authoritative command execution. */
  public static void registerServerReceivers() {
    ServerPlayNetworking.registerGlobalReceiver(
        RunCommandC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          // Network thread -> hop to the server thread before touching the world.
          context.server().execute(() -> handleRun(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        ProgramActionC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleProgram(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        RequestInventoryC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleInventoryRequest(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        TurtleInventoryActionC2S.TYPE,
        (payload, context) ->
            context.server().execute(() -> handleInventoryAction(context.player(), payload)));
  }

  private static void handleInventoryRequest(ServerPlayer player, RequestInventoryC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR) {
      return;
    }
    if (!(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)) {
      return;
    }
    sendInventory(player, turtle);
  }

  private static void handleInventoryAction(ServerPlayer player, TurtleInventoryActionC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR
        || !(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)
        || TurtleManager.isRunning(level, pos)) {
      return;
    }
    NonNullList<ItemStack> inventory = turtle.getInventory();
    int slot = Math.max(0, Math.min(inventory.size() - 1, p.slot()));
    turtle.selectInventorySlot(slot);
    if (p.action() == TurtleInventoryActionC2S.INSERT_HELD) {
      ItemStack held = player.getMainHandItem();
      ItemStack existing = inventory.get(slot);
      if (!held.isEmpty()
          && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, held))) {
        int capacity =
            existing.isEmpty()
                ? held.getMaxStackSize()
                : existing.getMaxStackSize() - existing.getCount();
        int moved = Math.min(capacity, held.getCount());
        if (moved > 0) {
          if (existing.isEmpty()) inventory.set(slot, held.copyWithCount(moved));
          else existing.grow(moved);
          held.shrink(moved);
          turtle.markChanged();
        }
      }
    } else if (p.action() == TurtleInventoryActionC2S.EXTRACT) {
      ItemStack extracted = inventory.get(slot);
      if (!extracted.isEmpty()) {
        inventory.set(slot, ItemStack.EMPTY);
        if (!player.getInventory().add(extracted)) player.drop(extracted, false);
        turtle.markChanged();
      }
    }
    sendInventory(player, turtle);
  }

  private static void sendInventory(ServerPlayer player, TurtleBlockEntity turtle) {
    NonNullList<ItemStack> inventory = turtle.getInventory();
    StringBuilder slots = new StringBuilder();
    for (int i = 0; i < inventory.size(); i++) {
      if (i > 0) {
        slots.append('\n');
      }
      ItemStack stack = inventory.get(i);
      if (!stack.isEmpty()) {
        slots
            .append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            .append(' ')
            .append(stack.getCount());
      }
    }
    ServerPlayNetworking.send(player, new InventoryS2C(turtle.getSelectedSlot(), slots.toString()));
  }

  private static void handleProgram(ServerPlayer player, ProgramActionC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR) {
      return;
    }
    if (!(level.getBlockEntity(pos) instanceof ProgramStore store)) {
      return;
    }
    if (p.action() == ProgramActionC2S.EJECT) {
      ejectDisk(player, pos, store);
      return;
    }
    if (!store.hasDisk()) {
      note(player, pos, store, "no disk inserted");
      return;
    }
    switch (p.action()) {
      case ProgramActionC2S.SAVE -> {
        store.saveProgram(p.name(), p.source());
        note(player, pos, store, "saved: " + p.name());
      }
      case ProgramActionC2S.LOAD -> {
        String source = store.loadProgram(p.name());
        if (source != null) {
          ServerPlayNetworking.send(player, new LoadProgramS2C(source));
        } else {
          note(player, pos, store, "no such program: " + p.name());
        }
      }
      case ProgramActionC2S.LIST ->
          note(
              player,
              pos,
              store,
              "programs: " + String.join(", ", new TreeSet<>(store.programNames())));
      case ProgramActionC2S.DELETE -> {
        store.deleteProgram(p.name());
        note(player, pos, store, "deleted: " + p.name());
      }
      default -> {}
    }
  }

  private static void ejectDisk(ServerPlayer player, BlockPos pos, ProgramStore store) {
    ItemStack disk = store.getDisk();
    if (disk.isEmpty()) {
      note(player, pos, store, "no disk to eject");
      return;
    }
    store.setDisk(ItemStack.EMPTY);
    player.drop(disk, false);
    note(player, pos, store, "ejected disk");
  }

  /** Sends a transient status line appended to the current transcript (not persisted). */
  private static void note(ServerPlayer player, BlockPos pos, ProgramStore store, String line) {
    ServerPlayNetworking.send(
        player, new TerminalScreenS2C(pos, store.getTranscript() + "\n" + line, false));
  }

  private static void handleRun(ServerPlayer player, RunCommandC2S payload) {
    Level level = player.level();
    BlockPos pos = payload.pos();
    double distSqr = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    if (distSqr > REACH_SQR) {
      return; // too far — ignore (anti-cheat / stale packet)
    }
    if (level.getBlockEntity(pos) instanceof TurtleBlockEntity) {
      // Turtle programs run tick-paced on a worker thread; the manager drives + replies.
      TurtleManager.run(level, pos, player, payload.command());
    } else if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
      computer.runCommand(payload.command());
      ServerPlayNetworking.send(
          player, new TerminalScreenS2C(pos, computer.getTranscript(), false));
    }
  }
}
