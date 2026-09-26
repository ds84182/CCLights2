package ds.mods.CCLights2.network;

import java.io.IOException;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.gpu.DrawCMD;

/**
 * Wire encoding for command arguments and events. Every value is prefixed with a type byte,
 * so nulls, primitive arrays and nested arrays all survive the trip.
 */
public final class Serialize {
	private Serialize() {}

	private static final byte T_NULL = 0;
	private static final byte T_INT = 1;
	private static final byte T_DOUBLE = 2;
	private static final byte T_STRING = 3;
	private static final byte T_BOOL = 4;
	private static final byte T_INT_ARRAY = 5;
	private static final byte T_BYTE_ARRAY = 6;
	private static final byte T_OBJ_ARRAY = 7;
	private static final byte T_LONG = 8;
	private static final byte T_FLOAT = 9;
	private static final byte T_CHAR = 10;
	private static final byte T_FLOAT_ARRAY = 11;

	public static void write(ByteArrayDataOutput out, Object o) {
		if (o == null) {
			out.writeByte(T_NULL);
		} else if (o instanceof Integer || o instanceof Short || o instanceof Byte) {
			out.writeByte(T_INT);
			out.writeInt(((Number) o).intValue());
		} else if (o instanceof Double) {
			out.writeByte(T_DOUBLE);
			out.writeDouble((Double) o);
		} else if (o instanceof Float) {
			out.writeByte(T_FLOAT);
			out.writeFloat((Float) o);
		} else if (o instanceof Long) {
			out.writeByte(T_LONG);
			out.writeLong((Long) o);
		} else if (o instanceof String) {
			out.writeByte(T_STRING);
			writeString(out, (String) o);
		} else if (o instanceof Boolean) {
			out.writeByte(T_BOOL);
			out.writeBoolean((Boolean) o);
		} else if (o instanceof Character) {
			out.writeByte(T_CHAR);
			out.writeChar((Character) o);
		} else if (o instanceof int[]) {
			int[] a = (int[]) o;
			out.writeByte(T_INT_ARRAY);
			out.writeInt(a.length);
			for (int v : a) out.writeInt(v);
		} else if (o instanceof float[]) {
			float[] a = (float[]) o;
			out.writeByte(T_FLOAT_ARRAY);
			out.writeInt(a.length);
			for (float v : a) out.writeFloat(v);
		} else if (o instanceof byte[]) {
			byte[] a = (byte[]) o;
			out.writeByte(T_BYTE_ARRAY);
			out.writeInt(a.length);
			out.write(a);
		} else if (o instanceof Object[]) {
			Object[] a = (Object[]) o;
			out.writeByte(T_OBJ_ARRAY);
			out.writeInt(a.length);
			for (Object v : a) write(out, v);
		} else {
			throw new IllegalArgumentException(o.getClass().getName() + " is not serializable");
		}
	}

	public static Object read(ByteArrayDataInput in) throws IOException {
		byte t = in.readByte();
		switch (t) {
		case T_NULL: return null;
		case T_INT: return in.readInt();
		case T_DOUBLE: return in.readDouble();
		case T_FLOAT: return in.readFloat();
		case T_LONG: return in.readLong();
		case T_STRING: return readString(in);
		case T_BOOL: return in.readBoolean();
		case T_CHAR: return in.readChar();
		case T_INT_ARRAY: {
			int n = checkLength(in.readInt());
			int[] a = new int[n];
			for (int i = 0; i < n; i++) a[i] = in.readInt();
			return a;
		}
		case T_FLOAT_ARRAY: {
			int n = checkLength(in.readInt());
			float[] a = new float[n];
			for (int i = 0; i < n; i++) a[i] = in.readFloat();
			return a;
		}
		case T_BYTE_ARRAY: {
			int n = checkLength(in.readInt());
			byte[] a = new byte[n];
			in.readFully(a);
			return a;
		}
		case T_OBJ_ARRAY: {
			int n = checkLength(in.readInt());
			Object[] a = new Object[n];
			for (int i = 0; i < n; i++) a[i] = read(in);
			return a;
		}
		default:
			throw new IOException("unknown value type " + t);
		}
	}

	/** Strings are written as UTF-8 with an int length; writeUTF's 64K limit is too small for text. */
	public static void writeString(ByteArrayDataOutput out, String s) {
		byte[] b = s.getBytes(com.google.common.base.Charsets.UTF_8);
		out.writeInt(b.length);
		out.write(b);
	}

	public static String readString(ByteArrayDataInput in) throws IOException {
		int n = checkLength(in.readInt());
		byte[] b = new byte[n];
		in.readFully(b);
		return new String(b, com.google.common.base.Charsets.UTF_8);
	}

	public static void writeCommand(ByteArrayDataOutput out, DrawCMD c) {
		out.writeInt(c.cmd.ordinal());
		out.writeInt(c.args.length);
		for (Object v : c.args) write(out, v);
	}

	public static DrawCMD readCommand(ByteArrayDataInput in) throws IOException {
		int ord = in.readInt();
		if (ord < 0 || ord >= CommandEnum.VALUES.length) throw new IOException("unknown command " + ord);
		DrawCMD c = new DrawCMD();
		c.cmd = CommandEnum.VALUES[ord];
		int n = checkLength(in.readInt());
		c.args = new Object[n];
		for (int i = 0; i < n; i++) c.args[i] = read(in);
		return c;
	}

	private static int checkLength(int n) throws IOException {
		if (n < 0 || n > 64 * 1024 * 1024) throw new IOException("bad length " + n);
		return n;
	}
}
