// A resident daemon: keeps running after you close the terminal.
//
// Every second it reads the redstone level on the back face and shows it on a
// monitor in front, plus a blinking heartbeat. Close the terminal and it keeps
// going; run an empty program (or call clearTimers()) to stop it.

var screen = monitor.at("front"); // the monitor block in front of this computer
var beat = 0;

every(20, function () {
  // 20 ticks = 1 second.
  beat = (beat + 1) % 2;
  var power = redstone.getInput("back"); // 0..15, 0 when unpowered

  if (screen) {
    screen.setLine(1, "STATUS DAEMON " + (beat ? "*" : " "));
    screen.setLine(2, "back input: " + power);
    screen.setLine(3, power > 0 ? "ALARM: powered!" : "all quiet");
  }

  // Mirror "alarmed" out the front as a redstone signal, too.
  redstone.setOutput("up", power > 0 ? 15 : 0);
});

print("daemon started — you can close this terminal now");
