// Run this on a turtle standing on a flat, empty area with one clear block in front and behind.
// turtle.wait() makes each beat visible without wasting CPU in a busy loop.

function pause() {
  turtle.wait(20);
}

print("Turtle animation showcase");
pause();

print("turn left");
turtle.turnLeft();
pause();

print("turn right");
turtle.turnRight();
pause();

print("forward");
turtle.forward();
pause();

print("back");
turtle.back();
pause();

print("place");
turtle.place("minecraft:stone");
pause();

print("dig and pick up");
turtle.dig();
pause();

print("showcase complete");
