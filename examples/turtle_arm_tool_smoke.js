// Smoke test for the Turtle arm/tool slot.
// Put a block in front of the Turtle, equip different tools in the Arm slot,
// then compare dig speed, drops, durability, Silk Touch, and Fortune behavior.

print("arm tool smoke");
print("fuel:", turtle.getFuelLevel());

var before = turtle.inspect();
print("front before:", before || "(empty)");

if (turtle.detect()) {
  print("dig:", turtle.dig());
} else {
  print("nothing to dig");
}

var after = turtle.inspect();
print("front after:", after || "(empty)");

for (var i = 1; i <= 16; i++) {
  var count = turtle.getItemCount(i);
  if (count > 0) {
    print("slot", i, "count", count);
  }
}

print("done");
