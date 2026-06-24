// Use this on the middle Turtle in a three-Turtle line.
// Cable reaches its back NIC from the source and its front NIC toward the destination.

var incoming = net.nic("back");
var outgoing = net.nic("front");

incoming.setPromiscuous(true);
incoming.onReceive(function (frame) {
  print("forwarding: " + frame);
  outgoing.forward(frame);
});

print("Forwarder ready.");
print("input:  " + incoming.address());
print("output: " + outgoing.address());
