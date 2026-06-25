package com.hodzilla51.minesier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Operator-tunable MineSIer safety/resource limits. */
public final class MineSIerConfig {
  private static final String FILE_NAME = "minesier.properties";
  private static final Properties PROPERTIES = new Properties();

  public static int instructionObserveEvery = 100_000;
  public static long maxScriptInstructions = 200_000_000L;
  public static long maxCallbackInstructions = 100_000L;
  public static int maxNetworkQueuedEvents = 1_024;
  public static int maxNetworkEventsPerTick = 4;
  public static int maxInboxFrames = 64;
  public static int maxFrameBytes = 4 * 1024;
  public static int maxTranscriptLines = 200;
  public static int maxTurtleProgramTicks = 12_000;
  public static int maxTurtleWaitTicks = 20 * 60;

  private MineSIerConfig() {}

  public static void load() {
    Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    if (Files.exists(path)) {
      try (Reader reader = Files.newBufferedReader(path)) {
        PROPERTIES.load(reader);
      } catch (IOException e) {
        MineSIer.LOGGER.warn("Failed to read {}, using defaults", path, e);
      }
    }

    instructionObserveEvery =
        intValue("instructionObserveEvery", instructionObserveEvery, 1, 10_000_000);
    maxScriptInstructions =
        longValue("maxScriptInstructions", maxScriptInstructions, 1, Long.MAX_VALUE);
    maxCallbackInstructions =
        longValue("maxCallbackInstructions", maxCallbackInstructions, 1, Long.MAX_VALUE);
    maxNetworkQueuedEvents =
        intValue("maxNetworkQueuedEvents", maxNetworkQueuedEvents, 1, 1_000_000);
    maxNetworkEventsPerTick =
        intValue("maxNetworkEventsPerTick", maxNetworkEventsPerTick, 1, 10_000);
    maxInboxFrames = intValue("maxInboxFrames", maxInboxFrames, 1, 100_000);
    maxFrameBytes = intValue("maxFrameBytes", maxFrameBytes, 1, 1_048_576);
    maxTranscriptLines = intValue("maxTranscriptLines", maxTranscriptLines, 1, 10_000);
    maxTurtleProgramTicks =
        intValue("maxTurtleProgramTicks", maxTurtleProgramTicks, 1, 20 * 60 * 60);
    maxTurtleWaitTicks = intValue("maxTurtleWaitTicks", maxTurtleWaitTicks, 0, 20 * 60 * 60);

    writeDefaults(path);
  }

  private static int intValue(String key, int fallback, int min, int max) {
    String value = PROPERTIES.getProperty(key);
    if (value == null) {
      PROPERTIES.setProperty(key, Integer.toString(fallback));
      return fallback;
    }
    try {
      return Math.clamp(Integer.parseInt(value.trim()), min, max);
    } catch (NumberFormatException ignored) {
      MineSIer.LOGGER.warn("Invalid integer config {}={}, using {}", key, value, fallback);
      return fallback;
    }
  }

  private static long longValue(String key, long fallback, long min, long max) {
    String value = PROPERTIES.getProperty(key);
    if (value == null) {
      PROPERTIES.setProperty(key, Long.toString(fallback));
      return fallback;
    }
    try {
      return Math.clamp(Long.parseLong(value.trim()), min, max);
    } catch (NumberFormatException ignored) {
      MineSIer.LOGGER.warn("Invalid long config {}={}, using {}", key, value, fallback);
      return fallback;
    }
  }

  private static void writeDefaults(Path path) {
    try {
      Files.createDirectories(path.getParent());
      try (Writer writer = Files.newBufferedWriter(path)) {
        PROPERTIES.store(writer, "MineSIer safety/resource limits");
      }
    } catch (IOException e) {
      MineSIer.LOGGER.warn("Failed to write {}", path, e);
    }
  }
}
