// A network file server: shares this computer's C: drive over the network so other
// computers can mount it as a drive. Pair it with examples/netfs_drive.js.
//
// Setup: put Cable on this computer's "back" face, connect it to the client, run this
// once. It stays resident (keeps serving after you close the terminal). Note the MAC
// it prints — the client needs it as SERVER.
//
// Protocol (JSON over one frame): request {id, op, path, data} -> reply {id, ok, ...}.

var nic = net.nic("back");

function handle(req) {
  var path = "C:/" + (req.path || "");
  if (req.op === "read") {
    return { id: req.id, ok: true, data: fs.read(path) };
  }
  if (req.op === "write") {
    return { id: req.id, ok: fs.write(path, req.data) };
  }
  if (req.op === "list") {
    return { id: req.id, ok: true, names: fs.list(path) };
  }
  if (req.op === "delete") {
    return { id: req.id, ok: fs.remove(path) };
  }
  return { id: req.id, ok: false };
}

nic.onReceive(function (frame) {
  var req;
  try {
    req = JSON.parse(frame.data);
  } catch (e) {
    return; // not for us
  }
  if (!req || req.fs !== "req") return; // ignore non-protocol frames
  var reply = handle(req);
  reply.fs = "reply";
  nic.send(frame.source, JSON.stringify(reply));
});

print("netfs server up. MAC: " + nic.address());
print("Set this as SERVER in netfs_drive.js on the client.");
