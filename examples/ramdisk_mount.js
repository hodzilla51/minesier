// Create your own virtual drive with fs.mount.
//
// This mounts an in-memory "RAM disk" as drive M:. Once mounted, M: behaves like the
// built-in C:/D: everywhere — fs.read/write/list, and the Files tree (press the
// reload button to see it appear). The backing store here is just a plain object,
// but a provider can be anything reachable from the sandbox (a network share, a
// computed/virtual filesystem, an encrypted vault, ...).
//
// A provider needs read/write/list; delete/exists/mkdir/listAll are optional refinements.
//
// Notes:
//   - Mounts are session-scoped: re-run this after a world reload to re-mount.
//   - C:/D: are reserved and can't be replaced; pick any other letter (E:..Z:).

var store = {}; // path (e.g. "notes/todo.txt") -> file text

fs.mount("M:", {
  // Return the file's text, or null when it doesn't exist.
  read: function (path) {
    return Object.prototype.hasOwnProperty.call(store, path) ? store[path] : null;
  },
  // Persist the text; return whether it succeeded.
  write: function (path, data) {
    store[path] = data;
    return true;
  },
  // Entry names in a directory ("" is the drive root). Append "/" to fold a name as
  // a folder. This flat RAM disk just lists every key.
  list: function (path) {
    return Object.keys(store);
  },
  // Optional:
  exists: function (path) {
    return Object.prototype.hasOwnProperty.call(store, path);
  },
  "delete": function (path) {
    var had = Object.prototype.hasOwnProperty.call(store, path);
    delete store[path];
    return had;
  }
});

// Drive it exactly like a built-in drive:
fs.write("M:/hello.txt", "hello from RAM");
print(fs.read("M:/hello.txt"));        // hello from RAM
print("mounts: " + fs.mounts().join(", "));
print("M: now appears in the Files tree (hit the reload button).");
