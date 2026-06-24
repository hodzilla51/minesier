// Put Cable on this turtle's back face. Replace the destination with the receiver's MAC.

var nic = net.nic("back");
var destination = "02:00:00:00:00:00"; // replace this

print("sent: " + nic.send(destination, "hello from a moving turtle"));
