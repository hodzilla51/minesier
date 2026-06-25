package com.hodzilla51.minesier.turtle;

import com.hodzilla51.minesier.ModContent;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registry for top-slot Turtle modules and their v1 script actions. */
public final class TurtleTopModules {
  private static final List<Module> MODULES =
      List.of(
          new Module(
              ModContent.PROXIMITY_SENSOR_MODULE, "Sensor", "Scan nearby blocks", true, 20, 1));

  private TurtleTopModules() {}

  public static boolean isTopModule(ItemStack stack) {
    return find(stack).isPresent();
  }

  public static Optional<Module> find(ItemStack stack) {
    if (stack.isEmpty()) {
      return Optional.empty();
    }
    return MODULES.stream().filter(module -> stack.is(module.item())).findFirst();
  }

  public record Module(
      Item item, String role, String summary, boolean providesScan, int scanTicks, int scanFuel) {}
}
