package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.LoadProgramS2C;
import com.hodzilla51.minesier.net.ProgramListS2C;
import com.hodzilla51.minesier.net.SwitchStatusS2C;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import com.hodzilla51.minesier.net.TurtleMoveS2C;
import com.hodzilla51.minesier.net.TurtleTurnS2C;
import com.hodzilla51.minesier.net.TurtleVisualS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class MineSIerClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    registerTooltips();

    // Server pushes terminal state: open the screen, or update the open one.
    ClientPlayNetworking.registerGlobalReceiver(
        TerminalScreenS2C.TYPE,
        (payload, context) ->
            context
                .client()
                .execute(
                    () -> {
                      if (payload.open()) {
                        context
                            .client()
                            .setScreenAndShow(
                                new ComputerScreen(
                                    payload.pos(), payload.transcript(), payload.turtle()));
                      } else {
                        ComputerScreen.updateIfOpen(payload.pos(), payload.transcript());
                      }
                    }));

    // Server signals a turtle hop: start a client-side slide animation.
    ClientPlayNetworking.registerGlobalReceiver(
        TurtleMoveS2C.TYPE,
        (payload, context) ->
            context
                .client()
                .execute(
                    () -> {
                      Direction fromDir = Direction.values()[payload.fromDir()];
                      long tick =
                          context.client().level != null
                              ? context.client().level.getGameTime()
                              : 0L;
                      TurtleAnimations.begin(payload.pos(), fromDir, tick, payload.durationTicks());
                    }));

    // Server signals a turtle turn: ease the model from its previous heading.
    ClientPlayNetworking.registerGlobalReceiver(
        TurtleTurnS2C.TYPE,
        (payload, context) ->
            context
                .client()
                .execute(
                    () -> {
                      long tick =
                          context.client().level != null
                              ? context.client().level.getGameTime()
                              : 0L;
                      // The blockstate already holds the NEW facing; start the model rotated back
                      // toward
                      // where it came from (a clockwise turn means it was 90° counter-clockwise
                      // before).
                      float deltaDeg = payload.clockwise() ? 90f : -90f;
                      TurtleAnimations.beginTurn(payload.pos(), deltaDeg, tick);
                    }));

    ClientPlayNetworking.registerGlobalReceiver(
        TurtleVisualS2C.TYPE,
        (payload, context) ->
            context
                .client()
                .execute(
                    () -> {
                      long tick =
                          context.client().level != null
                              ? context.client().level.getGameTime()
                              : 0L;
                      TurtleAnimations.beginEffect(
                          payload.pos(), payload.action(), payload.detail(), tick);
                    }));

    // Server sends a saved program's source to drop into the editor.
    ClientPlayNetworking.registerGlobalReceiver(
        LoadProgramS2C.TYPE,
        (payload, context) ->
            context.client().execute(() -> ComputerScreen.loadIntoEditor(payload.source())));

    // Server sends the disk's program names for the file-tree pane.
    ClientPlayNetworking.registerGlobalReceiver(
        ProgramListS2C.TYPE,
        (payload, context) ->
            context.client().execute(() -> ComputerScreen.showPrograms(payload.names())));

    ClientPlayNetworking.registerGlobalReceiver(
        SwitchStatusS2C.TYPE,
        (payload, context) ->
            context
                .client()
                .execute(
                    () ->
                        context
                            .client()
                            .setScreenAndShow(
                                new SwitchStatusScreen(payload.pos(), payload.status()))));

    // The vanilla storage menu for turtles renders through our terminal-styled screen.
    MenuScreens.register(ModContent.TURTLE_MENU, TurtleScreen::new);
    MenuScreens.register(ModContent.TURTLE_EQUIPMENT_MENU, TurtleEquipmentScreen::new);

    // The turtle block is INVISIBLE; this renderer draws it (and slides it on moves).
    BlockEntityRendererRegistry.register(
        ModContent.TURTLE_BLOCK_ENTITY, TurtleBlockEntityRenderer::new);

    // Draws the monitor's text buffer on its screen face.
    BlockEntityRendererRegistry.register(
        ModContent.MONITOR_BLOCK_ENTITY, MonitorBlockEntityRenderer::new);
  }

  private static void registerTooltips() {
    ItemTooltipCallback.EVENT.register(
        (stack, context, flag, lines) -> {
          if (stack.is(ModContent.COMPUTER_BLOCK.asItem())) {
            guide(lines, "computer.1", "computer.2", "computer.3");
          } else if (stack.is(ModContent.TURTLE_BLOCK.asItem())) {
            guide(lines, "turtle.1", "turtle.2", "turtle.3");
          } else if (stack.is(ModContent.DISK)) {
            guide(lines, "disk.1", "disk.2");
          } else if (stack.is(ModContent.CABLE_BLOCK.asItem())) {
            guide(lines, "cable.1", "cable.2");
          } else if (stack.is(ModContent.SWITCH_BLOCK.asItem())) {
            guide(lines, "switch.1", "switch.2");
          } else if (stack.is(ModContent.WIRELESS_MODEM_BLOCK.asItem())) {
            guide(lines, "wireless_modem.1", "wireless_modem.2");
          } else if (stack.is(ModContent.MONITOR_BLOCK.asItem())) {
            guide(lines, "monitor.1", "monitor.2");
          } else if (stack.is(ModContent.WHEEL_FOOT_PART)) {
            guide(lines, "wheel_foot_part.1");
          } else if (stack.is(ModContent.CRAWLER_FOOT_PART)) {
            guide(lines, "crawler_foot_part.1");
          } else if (stack.is(ModContent.HOVER_FOOT_PART)) {
            guide(lines, "hover_foot_part.1");
          } else if (stack.is(ModContent.PROXIMITY_SENSOR_MODULE)) {
            guide(lines, "proximity_sensor_module.1");
          }
        });
  }

  private static void guide(java.util.List<Component> lines, String... keys) {
    for (String key : keys) {
      lines.add(Component.translatable("tooltip.minesier." + key));
    }
  }
}
