package com.hodzilla51.minesier.js;

import java.util.ArrayList;
import java.util.List;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import com.hodzilla51.minesier.net.NetworkFrame;

/**
 * One sandboxed JavaScript VM, owned by a single computer (1 block = 1 VM).
 *
 * <p>The scope is created once and reused across {@link #run(String)} calls, so
 * variables and functions persist between commands like a REPL/shell. Programs
 * emit lines via the injected {@code print(...)} global; {@link #run} returns
 * everything printed, followed by the final expression value (if any).
 *
 * <p>Sandboxing (the M1 trust boundary): {@code initSafeStandardObjects()} omits
 * {@code Packages}/{@code java}/{@code getClass}, a deny-all {@link ClassShutter}
 * blocks any residual class resolution, and interpreted mode avoids bytecode
 * generation under Knot's classloader.
 */
public final class JsComputer {
	/** Hide every Java class from scripts — nothing is visible. */
	private static final ClassShutter DENY_ALL = fullClassName -> false;

	private Scriptable scope;

	/** Output sink for the currently-running program; null when idle. */
	private List<String> sink;

	/** Turtle actions for the {@code turtle} global; null on a plain (non-turtle) computer. */
	private TurtleApi turtle;
	/** Network actions for the {@code net} global; null until a network-capable owner attaches one. */
	private NetworkApi network;

	/** Attaches the turtle this VM controls; call before {@link #run} on a turtle. */
	public void setTurtle(TurtleApi turtle) {
		this.turtle = turtle;
	}

	/** Attaches the network interface owned by this VM's block entity. */
	public void setNetwork(NetworkApi network) {
		this.network = network;
	}

	/** Stops all event handlers owned by this VM. Called before a new terminal program runs. */
	public void clearReceiveHandlers() {
		if (network != null) {
			network.clearReceiveListeners();
		}
	}

	/** Invokes one registered receive callback within a small, tick-safe instruction budget. */
	private synchronized void invokeReceiveHandler(Function handler, NetworkFrame frame) {
		List<String> out = new ArrayList<>();
		SafeContextFactory.resetCounter(100_000L);
		Context cx = Context.enter();
		try {
			cx.setInterpretedMode(true);
			try {
				cx.setClassShutter(DENY_ALL);
			} catch (SecurityException alreadySet) {
				// A pooled context may already have the deny-all shutter.
			}
			this.sink = out;
			handler.call(cx, scope, scope, new Object[] { receiveFrameObject(cx, scope, frame) });
		} catch (RhinoException | IllegalStateException e) {
			out.add("error: " + e.getMessage());
		} finally {
			this.sink = null;
			Context.exit();
		}
		NetworkApi net = network;
		if (net != null) {
			net.reportOutput(out);
		}
	}

	private Object receiveFrameObject(Context cx, Scriptable scope, NetworkFrame frame) {
		ScriptableObject object = (ScriptableObject) cx.newObject(scope);
		ScriptableObject.putProperty(object, "source", frame.source());
		ScriptableObject.putProperty(object, "destination", frame.destination());
		ScriptableObject.putProperty(object, "data", frame.data());
		ScriptableObject.putProperty(object, "toString", new BaseFunction() {
			@Override
			public Object call(Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
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
				if (turtle != null) {
					ScriptableObject turtleObj = (ScriptableObject) cx.newObject(scope);
					for (String op : new String[] {
						"forward", "back", "turnLeft", "turnRight",
						"dig", "place", "detect", "inspect", "getFuelLevel", "refuel",
						"select", "getSelectedSlot", "getItemCount"
					}) {
						ScriptableObject.putProperty(turtleObj, op, new TurtleFunction(op));
					}
					ScriptableObject.putProperty(scope, "turtle", turtleObj);
				}
				if (network != null) {
					ScriptableObject netObj = (ScriptableObject) cx.newObject(scope);
					for (String op : new String[] { "address", "send", "receive", "nic" }) {
						ScriptableObject.putProperty(netObj, op, new NetworkFunction(op));
					}
					ScriptableObject.putProperty(scope, "net", netObj);
				}
			}
			this.sink = out;
			Object result = cx.evaluateString(scope, source, "computer", 1, null);
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
				case "send" -> args.length >= 2
					? net.send(Context.toString(args[0]), Context.toString(args[1])) : Boolean.FALSE;
				case "receive" -> toScriptFrame(cx, scope, net.receive());
				case "nic" -> args.length >= 1 ? createNicObject(cx, scope, net, Context.toString(args[0])) : null;
				default -> null;
			};
		}

		private Object createNicObject(Context cx, Scriptable scope, NetworkApi net, String interfaceName) {
			if (net.address(interfaceName) == null) {
				return null;
			}
			ScriptableObject object = (ScriptableObject) cx.newObject(scope);
			for (String op : new String[] {
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
			ScriptableObject.putProperty(object, "toString", new BaseFunction() {
				@Override
				public Object call(Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
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
				case "send" -> args.length >= 2
					? net.send(interfaceName, Context.toString(args[0]), Context.toString(args[1])) : Boolean.FALSE;
				case "receive" -> toScriptFrame(cx, scope, net.receive(interfaceName));
				case "forward" -> args.length >= 1 && args[0] instanceof Scriptable frame
					? net.forward(interfaceName, fromScriptFrame(frame)) : Boolean.FALSE;
				case "setPromiscuous" -> args.length >= 1
					? net.setPromiscuous(interfaceName, Context.toBoolean(args[0])) : Boolean.FALSE;
				case "onReceive" -> args.length >= 1 && args[0] instanceof Function callback
					? net.setReceiveListener(interfaceName, frame -> invokeReceiveHandler(callback, frame)) : Boolean.FALSE;
				case "offReceive" -> net.clearReceiveListener(interfaceName);
				default -> null;
			};
		}

		private NetworkFrame fromScriptFrame(Scriptable frame) {
			return new NetworkFrame(
				Context.toString(ScriptableObject.getProperty(frame, "source")),
				Context.toString(ScriptableObject.getProperty(frame, "destination")),
				Context.toString(ScriptableObject.getProperty(frame, "data")));
		}

		private Object toScriptFrame(Context cx, Scriptable scope, NetworkFrame frame) {
			if (frame == null) {
				return null;
			}
			ScriptableObject object = (ScriptableObject) cx.newObject(scope);
			ScriptableObject.putProperty(object, "source", frame.source());
			ScriptableObject.putProperty(object, "destination", frame.destination());
			ScriptableObject.putProperty(object, "data", frame.data());
			ScriptableObject.putProperty(object, "toString", new BaseFunction() {
				@Override
				public Object call(Context context, Scriptable scriptScope, Scriptable thisObj, Object[] args) {
					return frame.source() + " -> " + frame.destination() + ": " + frame.data();
				}
			});
			return object;
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
				case "turnLeft" -> t.turnLeft();
				case "turnRight" -> t.turnRight();
				case "dig" -> t.dig();
				case "place" -> args.length > 0 ? t.place(Context.toString(args[0])) : t.placeSelected();
				case "detect" -> t.detect();
				case "inspect" -> t.inspect();
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
				case "getItemCount" -> t.getItemCount(args.length > 0 ? (int) Context.toNumber(args[0]) : 0);
				default -> Boolean.FALSE;
			};
		}
	}
}
