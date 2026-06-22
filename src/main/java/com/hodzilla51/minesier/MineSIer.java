package com.hodzilla51.minesier;

import com.mojang.brigadier.arguments.StringArgumentType;

import com.hodzilla51.minesier.js.SafeContextFactory;
import com.hodzilla51.minesier.net.MineSIerNet;
import com.hodzilla51.minesier.turtle.TurtleManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineSIer implements ModInitializer {
	public static final String MOD_ID = "minesier";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		// Sandbox + anti-runaway for every script context (install before any script runs).
		SafeContextFactory.install();

		// Register blocks / items / block entities (the Computer block).
		ModContent.init();

		// Terminal networking: payload codecs (both sides) + server-side command receiver.
		MineSIerNet.registerPayloads();
		MineSIerNet.registerServerReceivers();

		// Tick-paced turtle execution (drives running programs once per server tick).
		TurtleManager.init();

		// /js <script> — evaluate a JavaScript snippet via the embedded Rhino engine.
		// Names follow MC 26.2 official (Mojang) mappings: Commands / CommandSourceStack / Component.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("js")
				.then(Commands.argument("script", StringArgumentType.greedyString())
					.executes(ctx -> {
						String script = StringArgumentType.getString(ctx, "script");
						try {
							String result = JsEngine.eval(script);
							ctx.getSource().sendSuccess(() -> Component.literal("= " + result), false);
							return 1;
						} catch (Exception e) {
							ctx.getSource().sendFailure(Component.literal("JS error: " + e.getMessage()));
							return 0;
						}
					}))));
	}
}
