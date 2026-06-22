package com.hodzilla51.minesier.js;

import java.util.ArrayList;
import java.util.List;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

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

	/** Attaches the turtle this VM controls; call before {@link #run} on a turtle. */
	public void setTurtle(TurtleApi turtle) {
		this.turtle = turtle;
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
