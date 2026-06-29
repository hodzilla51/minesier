package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.block.AccessControlledBlockEntity;
import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.ProgramStore;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.disk.DiskStorage;
import com.hodzilla51.minesier.disk.FileSystemProvider;
import com.hodzilla51.minesier.menu.TurtleEquipmentMenu;
import com.hodzilla51.minesier.menu.TurtleEquipmentMenuProvider;
import com.hodzilla51.minesier.menu.TurtleMenu;
import com.hodzilla51.minesier.menu.TurtleMenuProvider;
import com.hodzilla51.minesier.turtle.TurtleManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Registers MineSIer's payload types (both sides) and the server-side receiver. */
public final class MineSIerNet {
  /** How close (squared, blocks) a player must be to drive a terminal. */
  private static final double REACH_SQR = 8 * 8;

  private MineSIerNet() {}

  /** Must run on BOTH client and server (common init) so the codecs are known. */
  public static void registerPayloads() {
    PayloadTypeRegistry.serverboundPlay().register(RunCommandC2S.TYPE, RunCommandC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(AccessActionC2S.TYPE, AccessActionC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(ProgramActionC2S.TYPE, ProgramActionC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(OpenTurtleInventoryC2S.TYPE, OpenTurtleInventoryC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(OpenTurtleEquipmentC2S.TYPE, OpenTurtleEquipmentC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(OpenTurtleTerminalC2S.TYPE, OpenTurtleTerminalC2S.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(TurtleClickC2S.TYPE, TurtleClickC2S.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TerminalScreenS2C.TYPE, TerminalScreenS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TurtleMoveS2C.TYPE, TurtleMoveS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TurtleTurnS2C.TYPE, TurtleTurnS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TurtleVisualS2C.TYPE, TurtleVisualS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(LoadProgramS2C.TYPE, LoadProgramS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(ProgramListS2C.TYPE, ProgramListS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(SwitchStatusS2C.TYPE, SwitchStatusS2C.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(AccessPromptS2C.TYPE, AccessPromptS2C.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(StopProcessC2S.TYPE, StopProcessC2S.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(ProcessStateS2C.TYPE, ProcessStateS2C.CODEC);
  }

  /** Pushes the disk's program names to the player's open terminal (for the file tree pane). */
  public static void sendProgramList(ServerPlayer player, ProgramStore store) {
    if (!(store instanceof BlockEntity owner)) {
      return;
    }
    ServerPlayNetworking.send(
        player, new ProgramListS2C(owner.getBlockPos(), String.join("\n", store.programNames())));
  }

  /** Pushes an updated file tree to players tracking this device. */
  public static void refreshProgramListForViewers(BlockEntity owner, ProgramStore store) {
    if (!(owner.getLevel() instanceof ServerLevel serverLevel)) {
      return;
    }
    for (ServerPlayer player : PlayerLookup.tracking(serverLevel, owner.getBlockPos())) {
      if (store instanceof AccessControlledBlockEntity access && !access.canAccess(player)) {
        continue;
      }
      sendProgramList(player, store);
    }
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
        AccessActionC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleAccess(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        ProgramActionC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleProgram(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        OpenTurtleInventoryC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleOpenInventory(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        OpenTurtleEquipmentC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleOpenEquipment(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        TurtleClickC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleTurtleClick(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        OpenTurtleTerminalC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleOpenTerminal(player, payload));
        });
    ServerPlayNetworking.registerGlobalReceiver(
        StopProcessC2S.TYPE,
        (payload, context) -> {
          ServerPlayer player = context.player();
          context.server().execute(() -> handleStop(player, payload));
        });
  }

  private static void handleAccess(ServerPlayer player, AccessActionC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR) {
      return;
    }
    if (level.getBlockEntity(pos) instanceof AccessControlledBlockEntity access) {
      access.handleAccessAction(player, p.action(), p.secret());
    }
  }

  private static void handleOpenInventory(ServerPlayer player, OpenTurtleInventoryC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR
        || !(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)
        || TurtleManager.isRunning(level, pos)
        || !turtle.ensureAccess(player)) {
      return;
    }
    player.openMenu(new TurtleMenuProvider(turtle, pos, p.screenWidth(), p.screenHeight()));
  }

  private static void handleOpenEquipment(ServerPlayer player, OpenTurtleEquipmentC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR
        || !(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)
        || TurtleManager.isRunning(level, pos)
        || !turtle.ensureAccess(player)) {
      return;
    }
    player.openMenu(
        new TurtleEquipmentMenuProvider(turtle, pos, p.screenWidth(), p.screenHeight()));
  }

  private static void handleTurtleClick(ServerPlayer player, TurtleClickC2S p) {
    // The menu validates reach + running state and owns the dupe-safe carried stack.
    if (player.containerMenu instanceof TurtleMenu menu) {
      if (p.slot() < 0) {
        menu.storeCarried();
      } else {
        menu.take(player, p.slot(), p.shift());
      }
    }
  }

  private static void handleOpenTerminal(ServerPlayer player, OpenTurtleTerminalC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR
        || !(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)
        || TurtleManager.isRunning(level, pos)
        || !turtle.ensureAccess(player)) {
      return;
    }
    if (isTurtleMenuAt(player, pos)) {
      // Do not send the normal close-menu packet: it briefly drops the client to the world,
      // recapturing and recentering the mouse before TerminalScreenS2C arrives.
      player.doCloseContainer();
    }
    ServerPlayNetworking.send(
        player, new TerminalScreenS2C(pos, turtle.getTranscript(), true, true));
    sendProgramList(player, turtle);
    ServerPlayNetworking.send(player, new ProcessStateS2C(false, ""));
  }

  private static boolean isTurtleMenuAt(ServerPlayer player, BlockPos pos) {
    if (player.containerMenu instanceof TurtleMenu menu) {
      return menu.turtlePos().equals(pos);
    }
    if (player.containerMenu instanceof TurtleEquipmentMenu menu) {
      return menu.turtlePos().equals(pos);
    }
    return false;
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
    if (store instanceof AccessControlledBlockEntity access && !access.ensureAccess(player)) {
      return;
    }
    if (p.action() == ProgramActionC2S.EJECT) {
      ejectDisk(player, pos, store);
      return;
    }
    // File operations route to C: (local) or D: (disk) based on path prefix.
    // Individual methods return false if the target storage is unavailable.
    switch (p.action()) {
      case ProgramActionC2S.SAVE -> {
        String[] dp = ProgramStore.parseDrivePath(p.name());
        if (dp == null) {
          note(player, pos, store, "save failed: null path");
          break;
        }
        String normalized = DiskStorage.normalizePath(dp[1]);
        if (normalized == null) {
          note(player, pos, store, "save failed: bad filename [" + dp[1] + "]");
          break;
        }
        FileSystemProvider provider = store.providerFor(dp[0]);
        if (provider == null) {
          note(player, pos, store, "save failed: drive " + dp[0] + " not mounted");
          break;
        }
        if (!provider.write(normalized, p.source())) {
          note(player, pos, store, "save failed: write error [" + normalized + "]");
          break;
        }
        note(player, pos, store, "saved: " + p.name());
        sendProgramList(player, store);
      }
      case ProgramActionC2S.LOAD -> {
        String source = store.readFile(p.name());
        if (source != null) {
          ServerPlayNetworking.send(player, new LoadProgramS2C(source));
        } else {
          note(player, pos, store, "no such file: " + p.name());
        }
      }
      case ProgramActionC2S.LIST -> sendProgramList(player, store);
      case ProgramActionC2S.DELETE -> {
        if (!store.deleteFile(p.name())) {
          note(player, pos, store, "delete failed: " + p.name());
        } else {
          note(player, pos, store, "deleted: " + p.name());
          sendProgramList(player, store);
        }
      }
      case ProgramActionC2S.RENAME -> {
        String source = store.readFile(p.name());
        if (source == null) {
          note(player, pos, store, "no such file: " + p.name());
          break;
        }
        if (!store.saveFile(p.newName(), source)) {
          note(player, pos, store, "rename failed (invalid destination): " + p.newName());
          break;
        }
        store.deleteFile(p.name());
        note(player, pos, store, "renamed: " + p.name() + " -> " + p.newName());
        sendProgramList(player, store);
      }
      case ProgramActionC2S.MKDIR -> {
        if (!store.mkdir(p.name())) {
          note(player, pos, store, "mkdir failed: " + p.name());
        } else {
          sendProgramList(player, store);
        }
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
    sendProgramList(player, store);
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
    if (level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
      if (!turtle.ensureAccess(player)) {
        return;
      }
      // Turtle programs run tick-paced on a worker thread; the manager drives + replies.
      TurtleManager.run(level, pos, player, payload.command());
    } else if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
      if (!computer.ensureAccess(player)) {
        return;
      }
      computer.runCommand(payload.command());
      ServerPlayNetworking.send(
          player, new TerminalScreenS2C(pos, computer.getTranscript(), false));
      ServerPlayNetworking.send(
          player, new ProcessStateS2C(computer.isRunning(), computer.getProcessName()));
    }
  }

  private static void handleStop(ServerPlayer player, StopProcessC2S payload) {
    Level level = player.level();
    BlockPos pos = payload.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR) {
      return;
    }
    if (level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
      if (!turtle.ensureAccess(player)) {
        return;
      }
      // Stops a foreground program or a resident daemon; the manager writes back + replies on the
      // next tick (finish()), so there's no immediate transcript push here.
      TurtleManager.stop(level, pos);
      ServerPlayNetworking.send(player, new ProcessStateS2C(false, ""));
      return;
    }
    if (!(level.getBlockEntity(pos) instanceof ComputerBlockEntity computer)) {
      return;
    }
    if (!computer.ensureAccess(player)) {
      return;
    }
    computer.stopResident();
    ServerPlayNetworking.send(player, new TerminalScreenS2C(pos, computer.getTranscript(), false));
    ServerPlayNetworking.send(player, new ProcessStateS2C(false, ""));
  }
}
