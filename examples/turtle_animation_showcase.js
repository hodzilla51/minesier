// Put one stone block in front of the turtle, then run this on a flat area with its back clear.
// The turtle mines that stone, picks it up, then places the acquired item from its selected slot.
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

print("dig and pick up");
turtle.dig();
pause();

print("place selected item");
turtle.place();
pause();

print("showcase complete");
