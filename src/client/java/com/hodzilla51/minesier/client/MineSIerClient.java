package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.InventoryS2C;
import com.hodzilla51.minesier.net.LoadProgramS2C;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import com.hodzilla51.minesier.net.TurtleMoveS2C;
import com.hodzilla51.minesier.net.TurtleTurnS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.core.Direction;

public class MineSIerClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
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
                      TurtleAnimations.begin(payload.pos(), fromDir, tick);
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

    // Server sends a saved program's source to drop into the editor.
    ClientPlayNetworking.registerGlobalReceiver(
        LoadProgramS2C.TYPE,
        (payload, context) ->
            context.client().execute(() -> ComputerScreen.loadIntoEditor(payload.source())));

    // Server replies with a turtle inventory snapshot for the read-only viewer.
    ClientPlayNetworking.registerGlobalReceiver(
        InventoryS2C.TYPE,
        (payload, context) ->
            context
                .client()
                .execute(() -> ComputerScreen.showInventory(payload.selected(), payload.slots())));

    // The turtle block is INVISIBLE; this renderer draws it (and slides it on moves).
    BlockEntityRendererRegistry.register(
        ModContent.TURTLE_BLOCK_ENTITY, TurtleBlockEntityRenderer::new);
  }
}
