// Smoke test for Turtle foot parts and variable movement pacing.
// Run with different foot parts equipped, then compare movement speed and fuel use.

print("movement smoke");
print("fuel before:", turtle.getFuelLevel());

print("forward");
print(turtle.forward());

print("back");
print(turtle.back());

print("up");
print(turtle.up());

print("down");
print(turtle.down());

print("turn left/right should stay fixed-speed");
print(turtle.turnLeft());
print(turtle.turnRight());

print("fuel after:", turtle.getFuelLevel());
print("done");
