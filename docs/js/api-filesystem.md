# `fs` — Filesystem & Drives

The `fs` global is your text filesystem. Files hold strings (typically program
source or data). It is available on both Computers and Turtles.

```js
fs.list(path?)            // -> array of entry names in a directory
fs.read(path)             // -> file contents as a string, or null if missing
fs.write(path, text)      // -> true on success, false on failure
fs.remove(path)           // -> true on success, false on failure
fs.exists(path)           // -> true / false
fs.mount(drive, provider) // -> true if mounted (see "Mounting your own drive")
fs.unmount(drive)         // -> true if removed
fs.mounts()               // -> array of mounted drive letters, e.g. ["C:","D:"]
```

All operations are **synchronous** — they return immediately with the result.

## Drives and paths

A path may begin with a **drive prefix** like `C:/` or `D:/`. With no prefix,
the drive defaults to `C:`.

| Drive | What it is | Available |
|-------|------------|-----------|
| `C:` | Local storage, tied to this device | always |
| `D:` | The inserted **disk item's** storage | only when a disk is inserted |
| others | Player-mounted virtual drives | when you `fs.mount` them |

```js
fs.write("hello.js", "print('hi')")     // -> C:/hello.js
fs.write("C:/hello.js", "print('hi')")  // same thing, explicit
fs.write("D:/save.txt", "level=3")      // on the inserted disk
fs.read("C:/hello.js")                  // "print('hi')"
fs.exists("D:/save.txt")                // true / false
```

`D:` operations fail (return `false`/`null`) when no disk is inserted.

## Listing a directory

```js
fs.list("C:/")        // names in the C: root
fs.list("C:/lib")     // names in C:/lib
```

Sub-directory entries end with a trailing `/`. Files do not.

```js
// e.g. -> ["startup.js", "lib/", "notes.txt"]
```

## Folders

Folders are implicit: writing `C:/lib/util.js` creates the `lib/` folder. The
file-tree UI and `mkdir` (via the UI) can also create empty folders, which list
as a bare `"lib/"` entry until they contain something.

## Mounting your own drive

This is the **virtual filesystem (VFS)**: you can register a drive letter whose
backend is *your own JavaScript object*. Once mounted, every `fs` call (and
`require`) against that drive routes into your object.

```js
fs.mount(drive, provider)
```

- `drive` — a single letter plus colon, e.g. `"N:"` (case-insensitive). The
  built-in `C:` and `D:` **cannot** be replaced.
- `provider` — a JS object supplying the backend functions below.

### Provider object

| Function | Required | Purpose |
|----------|----------|---------|
| `read(path)` | yes | return file text, or `null`/undefined if missing |
| `write(path, data)` | yes | return `true` on success |
| `list(path)` | yes | return an array of entry names (dirs end with `/`) |
| `delete(path)` | optional | return `true` on success |
| `exists(path)` | optional | return `true`/`false` (defaults to `read != null`) |
| `mkdir(path)` | optional | return `true` on success |
| `listAll()` | optional | return *every* path under the drive (for the tree UI) |

Paths passed to your provider are **drive-relative and normalized** — no `N:/`
prefix, no leading/trailing slash.

```js
// A tiny in-memory RAM disk mounted at M:
const files = {}
fs.mount("M:", {
  read:  (p) => (p in files ? files[p] : null),
  write: (p, data) => { files[p] = data; return true },
  list:  () => Object.keys(files),
  delete:(p) => { delete files[p]; return true },
})

fs.write("M:/note.txt", "hello")
print(fs.read("M:/note.txt"))   // "hello"
```

Because the backend is just JavaScript, a drive can be anything reachable from
the sandbox: a RAM disk, computed/virtual files, or a **network share** backed
by the [`net`](api-network.md) API. See `examples/ramdisk_mount.js`,
`examples/netfs_server.js`, and `examples/netfs_drive.js` in the repo.

### Sync vs. async backends

`fs` is synchronous, but `net` is not. To back a drive with a remote server,
keep a **local mirror** (a plain object) that your provider reads/writes
synchronously, and sync it to the remote in the background with
[`every`](runtime.md#background-timers-every--after). `examples/netfs_drive.js`
demonstrates this pattern end to end.

### Lifetime

Player-mounted drives are **session-scoped** — they live until the device
unloads or the world reloads. Persisting mounts across reloads is planned but
not yet implemented, so re-mount them from a startup routine if you need them
back.
