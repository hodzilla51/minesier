package com.hodzilla51.minesier.net;

import com.hodzilla51.minesier.block.ComputerBlockEntity;
import com.hodzilla51.minesier.block.ProgramStore;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.item.DiskContents;
import com.hodzilla51.minesier.menu.TurtleEquipmentMenu;
import com.hodzilla51.minesier.menu.TurtleEquipmentMenuProvider;
import com.hodzilla51.minesier.menu.TurtleMenu;
import com.hodzilla51.minesier.menu.TurtleMenuProvider;
import com.hodzilla51.minesier.turtle.TurtleManager;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
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
  }

  /** Pushes the disk's program names to the player's open terminal (for the file tree pane). */
  public static void sendProgramList(ServerPlayer player, ProgramStore store) {
    ServerPlayNetworking.send(player, new ProgramListS2C(String.join("\n", store.programNames())));
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
  }

  private static void handleOpenInventory(ServerPlayer player, OpenTurtleInventoryC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR
        || !(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)
        || TurtleManager.isRunning(level, pos)) {
      return;
    }
    player.openMenu(new TurtleMenuProvider(turtle, pos, p.screenWidth(), p.screenHeight()));
  }

  private static void handleOpenEquipment(ServerPlayer player, OpenTurtleEquipmentC2S p) {
    Level level = player.level();
    BlockPos pos = p.pos();
    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQR
        || !(level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle)
        || TurtleManager.isRunning(level, pos)) {
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
        || TurtleManager.isRunning(level, pos)) {
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
        String path = DiskContents.normalizePath(p.name());
        if (path == null || !store.saveFile(path, p.source())) {
          note(player, pos, store, "invalid path: " + p.name());
        } else {
          note(player, pos, store, "saved: " + path);
          sendProgramList(player, store);
        }
      }
      case ProgramActionC2S.LOAD -> {
        String path = DiskContents.normalizePath(p.name());
        String source = path == null ? null : store.readFile(path);
        if (source != null) {
          ServerPlayNetworking.send(player, new LoadProgramS2C(source));
        } else {
          note(player, pos, store, "no such file: " + p.name());
        }
      }
      case ProgramActionC2S.LIST ->
          note(player, pos, store, formatProgramTree(store.programNames()));
      case ProgramActionC2S.DELETE -> {
        String path = DiskContents.normalizePath(p.name());
        if (path == null || !store.deleteFile(path)) {
          note(player, pos, store, "invalid path: " + p.name());
        } else {
          note(player, pos, store, "deleted: " + path);
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

  /**
   * Renders program names as an indented folder tree, treating {@code /} in a name as a path
   * separator (so {@code lib/crypto} and {@code net/router} group under their folders).
   */
  private static String formatProgramTree(Set<String> names) {
    if (names.isEmpty()) {
      return "programs: (none)";
    }
    DirNode root = new DirNode();
    for (String name : names) {
      DirNode node = root;
      String[] parts = name.split("/");
      for (int i = 0; i < parts.length - 1; i++) {
        node = node.dirs.computeIfAbsent(parts[i], k -> new DirNode());
      }
      node.files.add(parts[parts.length - 1]);
    }
    StringBuilder sb = new StringBuilder("programs:");
    renderTree(root, 1, sb);
    return sb.toString();
  }

  private static void renderTree(DirNode node, int depth, StringBuilder sb) {
    String indent = "  ".repeat(depth);
    for (var entry : node.dirs.entrySet()) {
      sb.append('\n').append(indent).append(entry.getKey()).append('/');
      renderTree(entry.getValue(), depth + 1, sb);
    }
    for (String file : node.files) {
      sb.append('\n').append(indent).append(file);
    }
  }

  /**
   * One level of the program folder tree: sub-folders (sorted) plus files (sorted) at this level.
   */
  private record DirNode(TreeMap<String, DirNode> dirs, TreeSet<String> files) {
    DirNode() {
      this(new TreeMap<>(), new TreeSet<>());
    }
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
