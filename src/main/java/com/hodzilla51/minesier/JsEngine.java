package com.hodzilla51.minesier;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Thin wrapper around Mozilla Rhino for evaluating one-off JavaScript snippets.
 *
 * <p>Each call enters its own {@link Context}, builds a fresh standard-object
 * scope, evaluates the source, and exits — so evaluations never share state.
 */
public final class JsEngine {
	private JsEngine() {
	}

	/**
	 * Evaluates a JavaScript expression and returns its result as a string.
	 *
	 * @param source the script to run
	 * @return the result coerced to a string (e.g. {@code "1+1"} -> {@code "2"})
	 */
	public static String eval(String source) {
		Context cx = Context.enter();
		try {
			// Interpreted mode skips on-the-fly bytecode generation, which is
			// fragile under Fabric's Knot classloader. Plenty fast for a command.
			cx.setInterpretedMode(true);
			Scriptable scope = cx.initStandardObjects();
			Object result = cx.evaluateString(scope, source, "<js>", 1, null);
			return Context.toString(result);
		} finally {
			Context.exit();
		}
	}
}
