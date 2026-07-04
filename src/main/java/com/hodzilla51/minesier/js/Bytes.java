package com.hodzilla51.minesier.js;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;
import org.mozilla.javascript.typedarrays.NativeArrayBufferView;

/**
 * Bridges raw {@code byte[]} and Rhino's typed-array world, and defines the mod's UTF-8 boundary
 * policy so binary content and text share one consistent story.
 *
 * <ul>
 *   <li><b>bytes → JS</b>: {@link #toUint8Array} hands scripts a {@code Uint8Array}.
 *   <li><b>JS → bytes</b>: {@link #toBytes} accepts a {@code Uint8Array}/{@code Int8Array}/any
 *       typed array, an {@code ArrayBuffer}, a plain number array, or (as a convenience) a string.
 *   <li><b>text decode</b>: {@link #decodeTextOrThrow} is <em>strict</em> — invalid UTF-8 throws
 *       {@link BinaryFileException} rather than silently yielding replacement characters, so
 *       callers can distinguish binary files from missing ones.
 *   <li><b>text encode</b>: {@link #encodeText} maps unpaired surrogates to U+FFFD (never silent
 *       corruption), matching the platform UTF-8 contract.
 * </ul>
 */
public final class Bytes {
  private Bytes() {}

  /** Wraps {@code data} in a fresh {@code Uint8Array} owned by {@code scope}. */
  public static Scriptable toUint8Array(Context cx, Scriptable scope, byte[] data) {
    Scriptable arr = cx.newObject(scope, "Uint8Array", new Object[] {Integer.valueOf(data.length)});
    NativeArrayBufferView view = (NativeArrayBufferView) arr;
    System.arraycopy(data, 0, view.getBuffer().getBuffer(), view.getByteOffset(), data.length);
    return arr;
  }

  /**
   * Converts a script value to bytes, or {@code null} if it is {@code null}/{@code undefined}.
   * Accepts typed arrays and {@code ArrayBuffer} (the byte-native path), plain number arrays, and
   * strings (UTF-8 encoded via {@link #encodeText}). Returns {@code null} for anything else.
   */
  public static byte[] toBytes(Object value) {
    if (value == null || Undefined.isUndefined(value)) {
      return null;
    }
    if (value instanceof byte[] raw) {
      return raw.clone();
    }
    if (value instanceof NativeArrayBuffer ab) {
      return ab.getBuffer().clone();
    }
    // Any typed-array view (Uint8Array, Int8Array, DataView, …): copy just this view's window.
    if (value instanceof NativeArrayBufferView view) {
      int off = view.getByteOffset();
      byte[] backing = view.getBuffer().getBuffer();
      byte[] out = new byte[view.getByteLength()];
      System.arraycopy(backing, off, out, 0, out.length);
      return out;
    }
    if (value instanceof CharSequence text) {
      return encodeText(text.toString());
    }
    // Plain JS array (or array-like) of numbers → one byte per element (low 8 bits).
    if (value instanceof Scriptable arr) {
      Object len = arr.get("length", arr);
      if (len instanceof Number n) {
        int count = n.intValue();
        if (count < 0) {
          return null;
        }
        byte[] out = new byte[count];
        for (int i = 0; i < count; i++) {
          out[i] = (byte) (long) Context.toNumber(arr.get(i, arr));
        }
        return out;
      }
    }
    return null;
  }

  /** Strict UTF-8 decode; throws {@link BinaryFileException} on any invalid byte sequence. */
  public static String decodeTextOrThrow(byte[] data, String path) {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(data)).toString();
    } catch (CharacterCodingException notText) {
      throw new BinaryFileException(path);
    }
  }

  /**
   * UTF-8 encodes {@code text}, replacing unpaired surrogates (which a JS string may legally carry)
   * with U+FFFD instead of throwing or emitting garbage — an explicit, lossy-but-safe boundary.
   */
  public static byte[] encodeText(String text) {
    CharsetEncoder encoder =
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            // An encoder's default substitute is '?'; use U+FFFD's UTF-8 bytes so an unpaired
            // surrogate becomes the Unicode replacement char, matching what a strict decode
            // expects.
            .replaceWith(new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});
    try {
      ByteBuffer buffer = encoder.encode(CharBuffer.wrap(text));
      byte[] out = new byte[buffer.remaining()];
      buffer.get(out);
      return out;
    } catch (CharacterCodingException impossible) {
      // REPLACE never reports an error; fall back to the platform encoder just in case.
      return text.getBytes(StandardCharsets.UTF_8);
    }
  }
}
