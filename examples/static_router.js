// A one-pass static router. The packet payload is JSON created by the sender:
// { "network": "remote", "payload": "hello" }
// Replace the next-hop address with the receiving NIC address on the remote segment.

var lan = net.nic("front");
var wan = net.nic("back");
var routes = {
  remote: "02:12:34:56:78:9a"
};

lan.setPromiscuous(true);

lan.onReceive(function (frame) {
  var packet = JSON.parse(frame.data);
  var nextHop = routes[packet.network];
  if (nextHop === undefined) {
    print("no route for", packet.network);
  } else {
    wan.send(nextHop, frame.data);
    print("routed", packet.network, "via", nextHop);
  }
});

print("router listening");
