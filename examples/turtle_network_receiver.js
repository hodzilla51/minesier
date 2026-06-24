// Put Cable on this turtle's back face, then run this once.
// Its receive handler remains active after the foreground program ends.

var nic = net.nic("back");
nic.onReceive(function (frame) {
  print("received: " + frame);
});

print("Turtle MAC: " + nic.address());
print("Waiting for frames on the back NIC.");
