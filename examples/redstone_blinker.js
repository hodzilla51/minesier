// Redstone I/O demo: mirror an input to an output, and pulse a third face.
//
// Wire a lever (or any signal) into the BACK face and a lamp off the FRONT
// face. While this program's poll loop runs the lamp follows the lever; the
// UP face emits a short analog ramp you can feed into a comparator.

// One-shot: latch the front output to whatever the back face currently reads.
// Outputs persist after the program ends, so the lamp stays put until changed.
var level = redstone.getInput("back"); // 0..15 (0 when unpowered)
redstone.setOutput("front", level);
print("front set to", redstone.getOutput("front"));

// Analog ramp out the UP face: 0,3,6,9,12,15. A comparator reads the strength.
for (var i = 0; i <= 15; i += 3) {
  redstone.setOutput("up", i);
}
print("up output ramped to", redstone.getOutput("up"));

// Clear every side back to off.
var sides = redstone.getSides();
for (var s = 0; s < sides.length; s++) {
  redstone.setOutput(sides[s], 0);
}
print("all sides cleared");
