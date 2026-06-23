// Connect two separate cable segments through a computer.
// This registers event handlers and then exits; forwarding happens only when frames arrive.

var front = net.nic("front");
var back = net.nic("back");

front.setPromiscuous(true);
back.setPromiscuous(true);

front.onReceive(function (frame) {
  back.forward(frame);
});

back.onReceive(function (frame) {
  front.forward(frame);
});

print("bridge listening");
