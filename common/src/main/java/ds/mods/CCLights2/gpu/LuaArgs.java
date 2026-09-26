package ds.mods.CCLights2.gpu;

import java.util.Map;

import dan200.computercraft.api.lua.LuaException;

/**
 * Helpers to validate and convert the Object[] ComputerCraft hands to peripherals.
 * Error messages follow the Lua convention: bad argument #n to 'name' (number expected, got nil).
 */
public final class LuaArgs {
	private LuaArgs() {}

	public static double getDouble(Object[] args, int i, String method) throws LuaException {
		if (i < args.length && args[i] instanceof Number) return ((Number) args[i]).doubleValue();
		throw bad(i, method, "number", args);
	}

	public static int getInt(Object[] args, int i, String method) throws LuaException {
		double d = getDouble(args, i, method);
		if (Double.isNaN(d) || Double.isInfinite(d)) throw bad(i, method, "number", args);
		return (int) Math.floor(d);
	}

	public static int optInt(Object[] args, int i, String method, int def) throws LuaException {
		if (i >= args.length || args[i] == null) return def;
		return getInt(args, i, method);
	}

	public static double optDouble(Object[] args, int i, String method, double def) throws LuaException {
		if (i >= args.length || args[i] == null) return def;
		return getDouble(args, i, method);
	}

	public static boolean optBool(Object[] args, int i, String method, boolean def) throws LuaException {
		if (i >= args.length || args[i] == null) return def;
		if (args[i] instanceof Boolean) return (Boolean) args[i];
		throw bad(i, method, "boolean", args);
	}

	public static String getString(Object[] args, int i, String method) throws LuaException {
		if (i < args.length && args[i] instanceof String) return (String) args[i];
		if (i < args.length && args[i] instanceof Number) return numberToString((Number) args[i]);
		throw bad(i, method, "string", args);
	}

	@SuppressWarnings("rawtypes")
	public static Map getTable(Object[] args, int i, String method) throws LuaException {
		if (i < args.length && args[i] instanceof Map) return (Map) args[i];
		throw bad(i, method, "table", args);
	}

	/** Reads n consecutive integers starting at index from. */
	public static int[] getInts(Object[] args, int from, int n, String method) throws LuaException {
		int[] out = new int[n];
		for (int k = 0; k < n; k++) out[k] = getInt(args, from + k, method);
		return out;
	}

	/** Boxes an int[] to an Object[] of Integers for use as command arguments. */
	public static Object[] box(int[] values) {
		Object[] out = new Object[values.length];
		for (int i = 0; i < values.length; i++) out[i] = values[i];
		return out;
	}

	/** Converts a Lua array table of numbers (1-based) into an int[]. */
	@SuppressWarnings("rawtypes")
	public static int[] tableToInts(Map m, int i, String method) throws LuaException {
		int n = m.size();
		int[] out = new int[n];
		for (int k = 0; k < n; k++) {
			Object v = m.get((double) (k + 1));
			if (!(v instanceof Number)) throw new LuaException("bad argument #" + (i + 1) + " to '" + method + "' (table entry " + (k + 1) + " is not a number)");
			out[k] = (int) Math.floor(((Number) v).doubleValue());
		}
		return out;
	}

	/** Converts a Lua array table of numbers (1-based) into raw bytes. */
	@SuppressWarnings("rawtypes")
	public static byte[] tableToBytes(Map m, int i, String method) throws LuaException {
		int n = m.size();
		byte[] out = new byte[n];
		for (int k = 0; k < n; k++) {
			Object v = m.get((double) (k + 1));
			if (!(v instanceof Number)) throw new LuaException("bad argument #" + (i + 1) + " to '" + method + "' (table entry " + (k + 1) + " is not a number)");
			out[k] = (byte) ((Number) v).intValue();
		}
		return out;
	}

	/** Lua strings are byte strings; CC gives them to us as chars 0-255. */
	public static byte[] stringToBytes(String s) {
		byte[] out = new byte[s.length()];
		for (int k = 0; k < out.length; k++) out[k] = (byte) s.charAt(k);
		return out;
	}

	public static int clampColor(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}

	public static LuaException bad(int i, String method, String expected, Object[] args) {
		String got = i < args.length && args[i] != null ? luaTypeName(args[i]) : "nil";
		return new LuaException("bad argument #" + (i + 1) + " to '" + method + "' (" + expected + " expected, got " + got + ")");
	}

	public static String luaTypeName(Object o) {
		if (o == null) return "nil";
		if (o instanceof Number) return "number";
		if (o instanceof String) return "string";
		if (o instanceof Boolean) return "boolean";
		if (o instanceof Map) return "table";
		return o.getClass().getSimpleName();
	}

	private static String numberToString(Number n) {
		double d = n.doubleValue();
		if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
		return String.valueOf(d);
	}
}
