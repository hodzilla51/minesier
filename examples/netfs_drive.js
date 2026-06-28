// Mount a remote computer's C: drive as a local network drive N: — shared storage
// between PCs, built entirely in JavaScript. Pair with examples/netfs_server.js.
//
// Setup: Cable on this computer's "back" face connected to the server, set SERVER to
// the MAC the server printed, then run this once.
//
// The catch: fs.read/write are synchronous, but the network is asynchronous. So this
// provider keeps a LOCAL MIRROR (cache) and serves reads from it, while a background
// loop + the receive handler keep the mirror fresh. This is the recommended pattern
// for any async-backed drive. Trade-off: a just-written remote file may take a moment
// to appear, and the first read of an uncached file returns null while it's fetched.

var SERVER = "00:00:00:00:00:00"; // <-- set to the server's MAC

var nic = net.nic("back");
var cache = {};   // path -> text (mirror of the server)
var names = [];   // root directory listing
var pending = {}; // request id -> path (to route read replies)
var nextId = 1;

function request(op, path, data) {
  var id = nextId++;
  if (op === "read") pending[id] = path;
  nic.send(SERVER, JSON.stringify({ fs: "req", id: id, op: op, path: path, data: data }));
  return id;
}

nic.onReceive(function (frame) {
  var msg;
  try {
    msg = JSON.parse(frame.data);
  } catch (e) {
    return;
  }
  if (!msg || msg.fs !== "reply") return;
  if (msg.names !== undefined) names = msg.names;            // list reply
  if (msg.id in pending) {                                    // read reply
    cache[pending[msg.id]] = msg.data;
    delete pending[msg.id];
  }
});

fs.mount("N:", {
  read: function (path) {
    if (Object.prototype.hasOwnProperty.call(cache, path)) return cache[path];
    request("read", path);  // fetch in the background; appears on a later read
    return null;
  },
  write: function (path, data) {
    cache[path] = data;     // optimistic local mirror
    request("write", path, data);
    return true;
  },
  list: function (path) {
    return names;
  },
  exists: function (path) {
    return Object.prototype.hasOwnProperty.call(cache, path);
  },
  "delete": function (path) {
    delete cache[path];
    request("delete", path);
    return true;
  }
});

// Keep the mirror fresh: refresh the listing once a second.
every(20, function () {
  request("list", "");
});
request("list", ""); // prime immediately

print("N: mounted (mirror of " + SERVER + "). Press the Files reload button to see it.");
