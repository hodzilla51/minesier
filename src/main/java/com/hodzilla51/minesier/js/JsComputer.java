package com.hodzilla51.minesier.js;

import com.hodzilla51.minesier.MineSIerConfig;
import com.hodzilla51.minesier.net.IpPacket;
import com.hodzilla51.minesier.net.NetworkFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * One sandboxed JavaScript VM, owned by a single computer (1 block = 1 VM).
 *
 * <p>The scope is created once and reused across {@link #run(String)} calls, so variables and
 * functions persist between commands like a REPL/shell. Programs emit lines via the injected {@code
 * print(...)} global; {@link #run} returns everything printed, followed by the final expression
 * value (if any).
 *
 * <p>Sandboxing (the M1 trust boundary): {@code initSafeStandardObjects()} omits {@code
 * Packages}/{@code java}/{@code getClass}, a deny-all {@link ClassShutter} blocks any residual
 * class resolution, and interpreted mode avoids bytecode generation under Knot's classloader.
 */
public final class JsComputer {
  public static final int API_VERSION = 1;
  public static final String API_VERSION_STRING = "1";

  /** Hide every Java class from scripts — nothing is visible. */
  private static final ClassShutter DENY_ALL = fullClassName -> false;

  private Scriptable scope;

  /** Output sink for the currently-running program; null when idle. */
  private List<String> sink;

  /** Optional live subscriber for newly printed transcript lines. */
  private Consumer<String> outputListener;

  /** Turtle actions for the {@code turtle} global; null on a plain (non-turtle) computer. */
  private TurtleApi turtle;

  /**
   * Network actions for the {@code net} global; null until a network-capable owner attaches one.
   */
  private NetworkApi network;

  /** Redstone I/O for the {@code redstone} global; null until a block-backed owner attaches one. */
  private RedstoneApi redstone;

  /** Monitor I/O for the {@code monitor} global; null until a block-backed owner attaches one. */
  private MonitorApi monitor;

  /** Text filesystem for the inserted disk; null until an owner attaches one. */
  private FileSystemApi fileSystem;

  /**
   * Background timers registered via {@code every}/{@code after}. Ticked once per server tick by
   * the owning block entity, so a program keeps working after its terminal closes (a resident
   * daemon).
   */
  private final List<Timer> timers = new ArrayList<>();

  /** Resolves a {@code require(name)} to another program's source (from the disk), or null. */
  private java.util.function.Function<String, String> moduleLoader;

  /** CommonJS module cache, keyed by required name; cleared when a new top-level program runs. */
  private final Map<String, Object> moduleCache = new HashMap<>();

  /** Attaches the turtle this VM controls; call before {@link #run} on a turtle. */
  public void setTurtle(TurtleApi turtle) {
    this.turtle = turtle;
  }

  /** Attaches the network interface owned by this VM's block entity. */
  public void setNetwork(NetworkApi network) {
    this.network = network;
  }

  /** Attaches the redstone I/O owned by this VM's block entity. */
  public void setRedstone(RedstoneApi redstone) {
    this.redstone = redstone;
  }

  /** Attaches the monitor I/O owned by this VM's block entity. */
  public void setMonitor(MonitorApi monitor) {
    this.monitor = monitor;
  }

  /** Attaches disk-backed file operations exposed as {@code fs}. */
  public void setFileSystem(FileSystemApi fileSystem) {
    this.fileSystem = fileSystem;
  }

  /** Attaches the {@code require(name)} resolver (maps a name to another program's source). */
  public void setModuleLoader(java.util.function.Function<String, String> moduleLoader) {
    this.moduleLoader = moduleLoader;
  }

  /** Attaches a live output listener for newly printed lines, or null to disable streaming. */
  public synchronized void setOutputListener(Consumer<String> outputListener) {
    this.outputListener = outputListener;
  }

  /**
   * Stops all background work owned by this VM: network receive callbacks and timers. Called before
   * a new terminal program runs, and when the owner is removed.
   */
  public synchronized void clearReceiveHandlers() {
    if (network != null) {
      network.clearReceiveListeners();
    }
    timers.clear();
    // Fresh module graph per top-level run, so edited library files reload.
    moduleCache.clear();
  }

  /** True while this VM has live timers (so its block entity keeps getting ticked). */
  public synchronized boolean hasTimers() {
    return !timers.isEmpty();
  }

  /**
   * Server tick: fires every timer whose countdown has elapsed, re-arming repeating ones and
   * dropping one-shots. Returns whatever the callbacks printed (for the transcript).
   */
  public synchronized List<String> tickTimers() {
    if (timers.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    // Snapshot so a callback that registers a new timer doesn't fire it this same tick.
    List<Timer> due = new ArrayList<>();
    for (Timer timer : new ArrayList<>(timers)) {
      if (--timer.remaining <= 0) {
        due.add(timer);
      }
    }
    for (Timer timer : due) {
      out.addAll(invokeBounded(timer.fn, null));
      if (timer.once) {
        timers.remove(timer);
      } else {
        timer.remaining = timer.interval;
      }
    }
    return out;
  }

  /** Invokes one registered receive callback within a small, tick-safe instruction budget. */
  private synchronized void invokeReceiveHandler(Function handler, NetworkFrame frame) {
    List<String> out =
        invokeBounded(handler, (cx, sc) -> new Object[] {receiveFrameObject(cx, sc, frame)});
    NetworkApi net = network;
    if (net != null) {
      net.reportOutput(out);
    }
  }

  /**
   * Runs a script callback against this VM's scope under the deny-all sandbox and a tick-safe
   * instruction budget. {@code argBuilder} (nullable) supplies the call arguments using the entered
   * context; printed output is collected and returned.
   */
  private List<String> invokeBounded(
      Function handler, BiFunction<Context, Scriptable, Object[]> argBuilder) {
    List<String> out = new ArrayList<>();
    SafeContextFactory.resetCounter(MineSIerConfig.maxCallbackInstructions);
    Context cx = Context.enter();
    try {
      cx.setInterpretedMode(true);
      try {
        cx.setClassShutter(DENY_ALL);
      } catch (SecurityException alreadySet) {
        // A pooled context may already have the deny-all shutter.
      }
      this.sink = out;
      Object[] args = argBuilder == null ? new Object[0] : argBuilder.apply(cx, scope);
      handler.call(cx, scope, scope, args);
      cx.processMicrotasks();
    } catch (RhinoException | IllegalStateException e) {
      out.add("error: " + e.getMessage());
    } finally {
      this.sink = null;
      Context.exit();
    }
    return out;
  }

  private Object receiveFrameObject(Context cx, Scriptable scope, NetworkFrame frame) {
    ScriptableObject object = (ScriptableObject) cx.newObject(scope);
    ScriptableObject.putProperty(object, "source", frame.source());
    ScriptableObject.putProperty(object, "destination", frame.destination());
    ScriptableObject.putProperty(object, "data", frame.data());
    ScriptableObject.putProperty(object, "hops", frame.hops());
    ScriptableObject.putProperty(
        object,
        "toString",
        new BaseFunction() {
          @Override
          public Object call(
              Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
            return frame.source() + " -> " + frame.destination() + ": " + frame.data();
          }
        });
    return object;
  }

  /**
   * Evaluates {@code source} against this computer's persistent scope.
   *
   * @return printed lines, then the final value as a line (errors as one line)
   */
  public synchronized List<String> run(String source) {
    List<String> out = new ArrayList<>();
    SafeContextFactory.resetCounter();
    Context cx = Context.enter();
    try {
      cx.setInterpretedMode(true);
      // A pooled Context may already carry a shutter; setting it twice throws.
      try {
        cx.setClassShutter(DENY_ALL);
      } catch (SecurityException alreadySet) {
        // Shutter already installed on this (reused) context — fine.
      }
      if (scope == null) {
        scope = cx.initSafeStandardObjects();
        ScriptableObject.putProperty(scope, "print", new PrintFunction());
        ScriptableObject minesierObj = (ScriptableObject) cx.newObject(scope);
        ScriptableObject.putProperty(minesierObj, "apiVersion", API_VERSION);
        ScriptableObject.putProperty(minesierObj, "apiVersionString", API_VERSION_STRING);
        ScriptableObject.putProperty(scope, "minesier", minesierObj);
        ScriptableObject cryptoObj = (ScriptableObject) cx.newObject(scope);
        for (String op :
            new String[] {
              "randomBytes", "sha256", "hmacSha256", "hkdfSha256",
              "aesGcmEncrypt", "aesGcmDecrypt", "x25519KeyPair", "x25519SharedSecret"
            }) {
          ScriptableObject.putProperty(cryptoObj, op, new CryptoFunction(op));
        }
        ScriptableObject.putProperty(scope, "crypto", cryptoObj);
        ScriptableObject ipObj = (ScriptableObject) cx.newObject(scope);
        for (String op : new String[] {"create", "encode", "decode", "forward"}) {
          ScriptableObject.putProperty(ipObj, op, new IpFunction(op));
        }
        ScriptableObject.putProperty(scope, "ip", ipObj);
        if (turtle != null) {
          ScriptableObject turtleObj = (ScriptableObject) cx.newObject(scope);
          for (String op :
              new String[] {
                "forward",
                "back",
                "up",
                "down",
                "turnLeft",
                "turnRight",
                "dig",
                "place",
                "wait",
                "detect",
                "inspect",
                "scan",
                "getFuelLevel",
                "refuel",
                "select",
                "getSelectedSlot",
                "getItemCount"
              }) {
            ScriptableObject.putProperty(turtleObj, op, new TurtleFunction(op));
          }
          ScriptableObject.putProperty(scope, "turtle", turtleObj);
        }
        if (network != null) {
          ScriptableObject netObj = (ScriptableObject) cx.newObject(scope);
          for (String op : new String[] {"address", "send", "receive", "nic", "broadcast"}) {
            ScriptableObject.putProperty(netObj, op, new NetworkFunction(op));
          }
          ScriptableObject.putProperty(scope, "net", netObj);
        }
        if (redstone != null) {
          ScriptableObject redstoneObj = (ScriptableObject) cx.newObject(scope);
          for (String op : new String[] {"getInput", "getOutput", "setOutput", "getSides"}) {
            ScriptableObject.putProperty(redstoneObj, op, new RedstoneFunction(op));
          }
          ScriptableObject.putProperty(scope, "redstone", redstoneObj);
        }
        if (monitor != null) {
          ScriptableObject monitorObj = (ScriptableObject) cx.newObject(scope);
          ScriptableObject.putProperty(monitorObj, "at", new MonitorFunction());
          ScriptableObject.putProperty(scope, "monitor", monitorObj);
        }
        if (fileSystem != null) {
          ScriptableObject fsObj = (ScriptableObject) cx.newObject(scope);
          for (String op : new String[] {"list", "read", "write", "remove", "exists"}) {
            ScriptableObject.putProperty(fsObj, op, new FileSystemFunction(op));
          }
          ScriptableObject.putProperty(scope, "fs", fsObj);
        }
        // Resident-execution timers: a program keeps running after its terminal closes.
        ScriptableObject.putProperty(scope, "every", new TimerRegisterFunction(false));
        ScriptableObject.putProperty(scope, "after", new TimerRegisterFunction(true));
        ScriptableObject.putProperty(scope, "clearTimers", new ClearTimersFunction());
        // CommonJS-style module loading from other programs on the disk.
        ScriptableObject.putProperty(scope, "require", new RequireFunction());
      }
      this.sink = out;
      Object result = cx.evaluateString(scope, source, "computer", 1, null);
      cx.processMicrotasks();
      if (result != null && !Undefined.isUndefined(result)) {
        out.add(Context.toString(result));
      }
    } catch (RhinoException e) {
      out.add("error: " + e.getMessage());
    } catch (Exception e) {
      out.add("error: " + e.getMessage());
    } finally {
      this.sink = null;
      Context.exit();
    }
    return out;
  }

  /** Cryptographic functions backed by the JDK's standard providers. */
  private final class CryptoFunction extends BaseFunction {
    private final String op;

    CryptoFunction(String op) {
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      return switch (op) {
        case "randomBytes" -> CryptoApi.randomBytes(integer(args, 0));
        case "sha256" -> CryptoApi.sha256(string(args, 0));
        case "hmacSha256" -> CryptoApi.hmacSha256(string(args, 0), string(args, 1));
        case "hkdfSha256" ->
            CryptoApi.hkdfSha256(
                string(args, 0), string(args, 1), string(args, 2), integer(args, 3));
        case "aesGcmEncrypt" ->
            encryptedObject(
                cx,
                scope,
                CryptoApi.aesGcmEncrypt(string(args, 0), string(args, 1), optionalString(args, 2)));
        case "aesGcmDecrypt" ->
            CryptoApi.aesGcmDecrypt(
                string(args, 0), string(args, 1), string(args, 2), optionalString(args, 3));
        case "x25519KeyPair" -> keyPairObject(cx, scope, CryptoApi.x25519KeyPair());
        case "x25519SharedSecret" -> CryptoApi.x25519SharedSecret(string(args, 0), string(args, 1));
        default -> null;
      };
    }

    private String string(Object[] args, int index) {
      if (args.length <= index)
        throw new IllegalArgumentException("missing argument " + (index + 1));
      return Context.toString(args[index]);
    }

    private String optionalString(Object[] args, int index) {
      return args.length > index ? Context.toString(args[index]) : "";
    }

    private int integer(Object[] args, int index) {
      if (args.length <= index)
        throw new IllegalArgumentException("missing argument " + (index + 1));
      return (int) Context.toNumber(args[index]);
    }

    private Object encryptedObject(Context cx, Scriptable scope, CryptoApi.Encrypted encrypted) {
      ScriptableObject object = (ScriptableObject) cx.newObject(scope);
      ScriptableObject.putProperty(object, "nonce", encrypted.nonce());
      ScriptableObject.putProperty(object, "ciphertext", encrypted.ciphertext());
      ScriptableObject.putProperty(
          object,
          "toString",
          new BaseFunction() {
            @Override
            public Object call(
                Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
              return "[AES-GCM ciphertext]";
            }
          });
      return object;
    }

    private Object keyPairObject(Context cx, Scriptable scope, CryptoApi.X25519KeyPair pair) {
      ScriptableObject object = (ScriptableObject) cx.newObject(scope);
      ScriptableObject.putProperty(object, "privateKey", pair.privateKey());
      ScriptableObject.putProperty(object, "publicKey", pair.publicKey());
      ScriptableObject.putProperty(
          object,
          "toString",
          new BaseFunction() {
            @Override
            public Object call(
                Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
              return "[X25519 key pair]";
            }
          });
      return object;
    }
  }

  /** IPv4-inspired packet helpers for player-written layer-3 protocols. */
  private final class IpFunction extends BaseFunction {
    private final String op;

    IpFunction(String op) {
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      return switch (op) {
        case "create" ->
            packetObject(
                cx,
                scope,
                new IpPacket(
                    requireString(args, 0),
                    requireString(args, 1),
                    optionalInteger(args, 4, 64),
                    requireInteger(args, 2),
                    requireString(args, 3)));
        case "encode" ->
            args.length >= 1 && args[0] instanceof Scriptable packet
                ? packetFromObject(packet).encode()
                : null;
        case "decode" ->
            args.length >= 1
                ? packetObject(cx, scope, IpPacket.decode(Context.toString(args[0])))
                : null;
        case "forward" ->
            args.length >= 1 && args[0] instanceof Scriptable packet
                ? packetObject(cx, scope, packetFromObject(packet).routed())
                : null;
        default -> null;
      };
    }

    private String requireString(Object[] args, int index) {
      if (args.length <= index) {
        throw new IllegalArgumentException("missing argument " + (index + 1));
      }
      return Context.toString(args[index]);
    }

    private int requireInteger(Object[] args, int index) {
      if (args.length <= index) {
        throw new IllegalArgumentException("missing argument " + (index + 1));
      }
      return (int) Context.toNumber(args[index]);
    }

    private int optionalInteger(Object[] args, int index, int defaultValue) {
      return args.length > index ? (int) Context.toNumber(args[index]) : defaultValue;
    }

    private IpPacket packetFromObject(Scriptable packet) {
      return new IpPacket(
          Context.toString(ScriptableObject.getProperty(packet, "source")),
          Context.toString(ScriptableObject.getProperty(packet, "destination")),
          (int) Context.toNumber(ScriptableObject.getProperty(packet, "ttl")),
          (int) Context.toNumber(ScriptableObject.getProperty(packet, "protocol")),
          Context.toString(ScriptableObject.getProperty(packet, "payload")));
    }

    private Object packetObject(Context cx, Scriptable scope, IpPacket packet) {
      if (packet == null) {
        return null;
      }
      ScriptableObject object = (ScriptableObject) cx.newObject(scope);
      ScriptableObject.putProperty(object, "version", 4);
      ScriptableObject.putProperty(object, "source", packet.source());
      ScriptableObject.putProperty(object, "destination", packet.destination());
      ScriptableObject.putProperty(object, "ttl", packet.ttl());
      ScriptableObject.putProperty(object, "protocol", packet.protocol());
      ScriptableObject.putProperty(object, "payload", packet.payload());
      ScriptableObject.putProperty(
          object,
          "toString",
          new BaseFunction() {
            @Override
            public Object call(
                Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
              return "IPv4 "
                  + packet.source()
                  + " -> "
                  + packet.destination()
                  + " ttl="
                  + packet.ttl()
                  + " protocol="
                  + packet.protocol();
            }
          });
      return object;
    }
  }

  /** A method on the {@code fs} global; delegates to the inserted disk filesystem. */
  private final class FileSystemFunction extends BaseFunction {
    private final String op;

    FileSystemFunction(String op) {
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      FileSystemApi fs = fileSystem;
      if (fs == null) {
        return switch (op) {
          case "list" -> cx.newArray(scope, new Object[0]);
          case "exists" -> Boolean.FALSE;
          case "write", "remove" -> Boolean.FALSE;
          default -> null;
        };
      }
      String path = args.length > 0 ? Context.toString(args[0]) : "/";
      return switch (op) {
        case "list" -> {
          List<String> names = fs.list(path);
          yield cx.newArray(scope, names.toArray(Object[]::new));
        }
        case "read" -> fs.read(path);
        case "write" ->
            args.length >= 2 ? fs.write(path, Context.toString(args[1])) : Boolean.FALSE;
        case "remove" -> fs.remove(path);
        case "exists" -> fs.exists(path);
        default -> null;
      };
    }
  }

  /** A method on the {@code net} global; delegates to its block entity's NIC. */
  private final class NetworkFunction extends BaseFunction {
    private final String op;

    NetworkFunction(String op) {
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      NetworkApi net = network;
      if (net == null) {
        return null;
      }
      return switch (op) {
        case "address" -> net.address();
        case "send" ->
            args.length >= 2
                ? net.send(Context.toString(args[0]), Context.toString(args[1]))
                : Boolean.FALSE;
        case "receive" -> toScriptFrame(cx, scope, net.receive());
        case "nic" ->
            args.length >= 1 ? createNicObject(cx, scope, net, Context.toString(args[0])) : null;
        case "broadcast" -> NetworkFrame.BROADCAST;
        default -> null;
      };
    }

    private Object createNicObject(
        Context cx, Scriptable scope, NetworkApi net, String interfaceName) {
      if (net.address(interfaceName) == null) {
        return null;
      }
      ScriptableObject object = (ScriptableObject) cx.newObject(scope);
      for (String op :
          new String[] {
            "address", "send", "receive", "forward", "setPromiscuous", "onReceive", "offReceive"
          }) {
        ScriptableObject.putProperty(object, op, new NicFunction(interfaceName, op));
      }
      return object;
    }

    private Object toScriptFrame(Context cx, Scriptable scope, NetworkFrame frame) {
      if (frame == null) {
        return null;
      }
      ScriptableObject object = (ScriptableObject) cx.newObject(scope);
      ScriptableObject.putProperty(object, "source", frame.source());
      ScriptableObject.putProperty(object, "destination", frame.destination());
      ScriptableObject.putProperty(object, "data", frame.data());
      ScriptableObject.putProperty(object, "hops", frame.hops());
      ScriptableObject.putProperty(
          object,
          "toString",
          new BaseFunction() {
            @Override
            public Object call(
                Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
              return frame.source() + " -> " + frame.destination() + ": " + frame.data();
            }
          });
      return object;
    }
  }

  /** A method on one physical network interface. */
  private final class NicFunction extends BaseFunction {
    private final String interfaceName;
    private final String op;

    NicFunction(String interfaceName, String op) {
      this.interfaceName = interfaceName;
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      NetworkApi net = network;
      if (net == null) {
        return null;
      }
      return switch (op) {
        case "address" -> net.address(interfaceName);
        case "send" ->
            args.length >= 2
                ? net.send(interfaceName, Context.toString(args[0]), Context.toString(args[1]))
                : Boolean.FALSE;
        case "receive" -> toScriptFrame(cx, scope, net.receive(interfaceName));
        case "forward" ->
            args.length >= 1 && args[0] instanceof Scriptable frame
                ? net.forward(interfaceName, fromScriptFrame(frame))
                : Boolean.FALSE;
        case "setPromiscuous" ->
            args.length >= 1
                ? net.setPromiscuous(interfaceName, Context.toBoolean(args[0]))
                : Boolean.FALSE;
        case "onReceive" ->
            args.length >= 1 && args[0] instanceof Function callback
                ? net.setReceiveListener(
                    interfaceName, frame -> invokeReceiveHandler(callback, frame))
                : Boolean.FALSE;
        case "offReceive" -> net.clearReceiveListener(interfaceName);
        default -> null;
      };
    }

    private NetworkFrame fromScriptFrame(Scriptable frame) {
      Object hops = ScriptableObject.getProperty(frame, "hops");
      int hopCount =
          hops instanceof Number n
              ? Math.max(0, n.intValue())
              : hops == Scriptable.NOT_FOUND ? 0 : (int) Math.max(0, Context.toNumber(hops));
      return new NetworkFrame(
          Context.toString(ScriptableObject.getProperty(frame, "source")),
          Context.toString(ScriptableObject.getProperty(frame, "destination")),
          Context.toString(ScriptableObject.getProperty(frame, "data")),
          hopCount);
    }

    private Object toScriptFrame(Context cx, Scriptable scope, NetworkFrame frame) {
      if (frame == null) {
        return null;
      }
      ScriptableObject object = (ScriptableObject) cx.newObject(scope);
      ScriptableObject.putProperty(object, "source", frame.source());
      ScriptableObject.putProperty(object, "destination", frame.destination());
      ScriptableObject.putProperty(object, "data", frame.data());
      ScriptableObject.putProperty(object, "hops", frame.hops());
      ScriptableObject.putProperty(
          object,
          "toString",
          new BaseFunction() {
            @Override
            public Object call(
                Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
              return frame.source() + " -> " + frame.destination() + ": " + frame.data();
            }
          });
      return object;
    }
  }

  /** A method on the {@code redstone} global; delegates to the live {@link RedstoneApi}. */
  private final class RedstoneFunction extends BaseFunction {
    private final String op;

    RedstoneFunction(String op) {
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      RedstoneApi rs = redstone;
      if (rs == null) {
        return Undefined.instance;
      }
      return switch (op) {
        case "getInput" -> args.length >= 1 ? rs.getInput(Context.toString(args[0])) : -1;
        case "getOutput" -> args.length >= 1 ? rs.getOutput(Context.toString(args[0])) : -1;
        case "setOutput" ->
            args.length >= 2
                ? rs.setOutput(Context.toString(args[0]), level(args[1]))
                : Boolean.FALSE;
        case "getSides" -> cx.newArray(scope, (Object[]) rs.sides());
        default -> Undefined.instance;
      };
    }

    /** Accepts a boolean (off/on -> 0/15) or a number (0..15) as the output level. */
    private int level(Object value) {
      if (value instanceof Boolean b) {
        return b ? 15 : 0;
      }
      return (int) Context.toNumber(value);
    }
  }

  /** The {@code monitor.at(side)} method: returns a handle to the monitor on that face, or null. */
  private final class MonitorFunction extends BaseFunction {
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      MonitorApi m = monitor;
      if (m == null || args.length < 1) {
        return null;
      }
      String side = Context.toString(args[0]);
      if (!m.exists(side)) {
        return null;
      }
      ScriptableObject handle = (ScriptableObject) cx.newObject(scope);
      for (String op : new String[] {"write", "setLine", "setText", "clear", "rows", "columns"}) {
        ScriptableObject.putProperty(handle, op, new MonitorHandleFunction(side, op));
      }
      return handle;
    }
  }

  /** A method on one monitor handle; delegates to the live {@link MonitorApi} for its side. */
  private final class MonitorHandleFunction extends BaseFunction {
    private final String side;
    private final String op;

    MonitorHandleFunction(String side, String op) {
      this.side = side;
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      MonitorApi m = monitor;
      if (m == null) {
        return Boolean.FALSE;
      }
      return switch (op) {
        case "write" -> m.write(side, args.length >= 1 ? Context.toString(args[0]) : "");
        case "setLine" ->
            args.length >= 2
                ? m.setLine(side, (int) Context.toNumber(args[0]), Context.toString(args[1]))
                : Boolean.FALSE;
        case "setText" -> m.setText(side, args.length >= 1 ? Context.toString(args[0]) : "");
        case "clear" -> m.clear(side);
        case "rows" -> m.rows(side);
        case "columns" -> m.columns(side);
        default -> Boolean.FALSE;
      };
    }
  }

  /**
   * A scheduled background callback. {@code remaining} counts down server ticks to the next fire.
   */
  private static final class Timer {
    final int interval;
    final Function fn;
    final boolean once;
    int remaining;

    Timer(int interval, Function fn, boolean once) {
      this.interval = interval;
      this.fn = fn;
      this.once = once;
      this.remaining = interval;
    }
  }

  /**
   * The {@code every(ticks, fn)} / {@code after(ticks, fn)} globals: register a repeating or
   * one-shot timer. A computer with any live timer keeps running after its terminal is closed.
   */
  private final class TimerRegisterFunction extends BaseFunction {
    private final boolean once;

    TimerRegisterFunction(boolean once) {
      this.once = once;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      if (args.length < 2 || !(args[1] instanceof Function fn)) {
        return Boolean.FALSE;
      }
      int interval = Math.max(1, (int) Context.toNumber(args[0]));
      synchronized (JsComputer.this) {
        timers.add(new Timer(interval, fn, once));
      }
      return Boolean.TRUE;
    }
  }

  /** The {@code clearTimers()} global: stops every background timer (the script-level "stop"). */
  private final class ClearTimersFunction extends BaseFunction {
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      synchronized (JsComputer.this) {
        timers.clear();
      }
      return Undefined.instance;
    }
  }

  /**
   * The {@code require(name)} global: loads another program from the disk as a CommonJS module and
   * returns its {@code module.exports}. Modules run in their own scope (top-level vars stay local)
   * but see the same globals; results are cached per run, and the cache is pre-seeded before
   * evaluation so circular requires resolve to the partial exports instead of looping.
   */
  private final class RequireFunction extends BaseFunction {
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      if (moduleLoader == null) {
        throw new IllegalStateException("require is unavailable here");
      }
      if (args.length < 1) {
        throw new IllegalArgumentException("require(name) needs a module name");
      }
      String name = Context.toString(args[0]);
      if (moduleCache.containsKey(name)) {
        return moduleCache.get(name);
      }
      String source = moduleLoader.apply(name);
      if (source == null) {
        throw new IllegalStateException("module not found: " + name);
      }
      Scriptable moduleScope = cx.newObject(JsComputer.this.scope);
      moduleScope.setParentScope(JsComputer.this.scope);
      ScriptableObject moduleObj = (ScriptableObject) cx.newObject(moduleScope);
      ScriptableObject exportsObj = (ScriptableObject) cx.newObject(moduleScope);
      ScriptableObject.putProperty(moduleObj, "exports", exportsObj);
      ScriptableObject.putProperty(moduleScope, "module", moduleObj);
      ScriptableObject.putProperty(moduleScope, "exports", exportsObj);
      ScriptableObject.putProperty(moduleScope, "require", this);
      moduleCache.put(name, exportsObj); // partial exports for circular requires
      cx.evaluateString(moduleScope, source, name, 1, null);
      cx.processMicrotasks();
      Object exported = ScriptableObject.getProperty(moduleObj, "exports");
      moduleCache.put(name, exported);
      return exported;
    }
  }

  /** The {@code print(...)} global: appends each argument (space-joined) to the output. */
  private final class PrintFunction extends BaseFunction {
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < args.length; i++) {
        if (i > 0) {
          sb.append(' ');
        }
        sb.append(Context.toString(args[i]));
      }
      if (sink != null) {
        // A printed value may contain newlines; keep each transcript line single-line.
        for (String line : sb.toString().split("\n", -1)) {
          sink.add(line);
          Consumer<String> listener = outputListener;
          if (listener != null) {
            listener.accept(line);
          }
        }
      }
      return Undefined.instance;
    }
  }

  /** A method on the {@code turtle} global; delegates to the live {@link TurtleApi}. */
  private final class TurtleFunction extends BaseFunction {
    private final String op;

    TurtleFunction(String op) {
      this.op = op;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      TurtleApi t = turtle;
      if (t == null) {
        return Boolean.FALSE;
      }
      return switch (op) {
        case "forward" -> t.forward();
        case "back" -> t.back();
        case "up" -> t.up();
        case "down" -> t.down();
        case "turnLeft" -> t.turnLeft();
        case "turnRight" -> t.turnRight();
        case "dig" -> t.dig();
        case "place" -> args.length > 0 ? t.place(Context.toString(args[0])) : t.placeSelected();
        case "wait" -> {
          t.waitTicks(args.length > 0 ? (int) Context.toNumber(args[0]) : 1);
          yield Undefined.instance;
        }
        case "detect" -> t.detect();
        case "inspect" -> t.inspect();
        case "scan" -> scanResults(cx, scope, t.scan());
        case "getFuelLevel" -> t.getFuelLevel();
        case "refuel" -> {
          t.refuel(args.length > 0 ? (int) Context.toNumber(args[0]) : 0);
          yield Undefined.instance;
        }
        case "select" -> {
          t.select(args.length > 0 ? (int) Context.toNumber(args[0]) : 1);
          yield Undefined.instance;
        }
        case "getSelectedSlot" -> t.getSelectedSlot();
        case "getItemCount" ->
            t.getItemCount(args.length > 0 ? (int) Context.toNumber(args[0]) : 0);
        default -> Boolean.FALSE;
      };
    }

    private Object scanResults(Context cx, Scriptable scope, List<TurtleApi.ScanResult> results) {
      Object[] entries = new Object[results.size()];
      for (int i = 0; i < results.size(); i++) {
        TurtleApi.ScanResult result = results.get(i);
        ScriptableObject object = (ScriptableObject) cx.newObject(scope);
        ScriptableObject.putProperty(object, "x", result.x());
        ScriptableObject.putProperty(object, "y", result.y());
        ScriptableObject.putProperty(object, "z", result.z());
        ScriptableObject.putProperty(object, "block", result.block());
        entries[i] = object;
      }
      return cx.newArray(scope, entries);
    }
  }
}
