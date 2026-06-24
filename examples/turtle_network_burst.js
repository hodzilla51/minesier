// Replace destination with the receiver's back-NIC MAC.
// The Turtle emits frames normally, while its cosmetic transfer animation is rate-limited.

var nic = net.nic("back");
var destination = "02:00:00:00:00:00"; // replace this

for (var i = 0; i < 30; i++) {
  nic.send(destination, "burst frame " + i);
}

print("burst sent");
