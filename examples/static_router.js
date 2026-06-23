// A static IPv4-style router. Replace the next-hop address with the receiving
// NIC address on the remote segment.

var lan = net.nic("front");
var wan = net.nic("back");
var routes = {
  "10.0.2": "02:12:34:56:78:9a"
};

lan.setPromiscuous(true);

lan.onReceive(function (frame) {
  var packet = ip.decode(frame.data);
  if (packet === null) return;
  var network = packet.destination.split(".").slice(0, 3).join(".");
  var nextHop = routes[network];
  if (nextHop === undefined) {
    print("no route for", packet.destination);
  } else {
    packet = ip.forward(packet);
    if (packet === null) {
      print("TTL expired");
    } else {
      wan.send(nextHop, ip.encode(packet));
      print("routed", packet.destination, "via", nextHop);
    }
  }
});

print("router listening");
