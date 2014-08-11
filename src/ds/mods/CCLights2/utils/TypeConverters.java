package ds.mods.CCLights2.utils;

import java.util.HashMap;
import java.util.Map;

public class TypeConverters {
	public static HashMap<String, String> types;
	{{
		types.put("java.lang.String", "string");
		types.put("java.lang.Double", "number");
		types.put("java.lang.Integer", "number");
		types.put("java.util.Map", "table");
		types.put("java.lang.Boolean", "boolean");
	}}
	
	public static String getLuaName(Object obj)
	{
		if (obj == null) return "nil";
		String typeName = obj.getClass().getName();
		return types.containsKey(typeName) ? types.get(typeName) : typeName+"!?";
	}
	
	public static Class getClass(Object obj)
	{
		if (obj == null) return null;
		return obj.getClass();
	}
	
	public static String toLString(Object obj)
	{
		if (!(obj instanceof String))
			throw new RuntimeException("string expected, got "+getLuaName(obj));
		return (String) obj;
	}
	
	public static Double toNumber(Object obj)
	{
		if (!(obj instanceof Double))
			throw new RuntimeException("number expected, got "+getLuaName(obj));
		return (Double) obj;
	}
	
	public static Integer toInteger(Object obj)
	{
		return toNumber(obj).intValue();
	}
	
	public static Map toTable(Object obj)
	{
		if (!(obj instanceof Map))
			throw new RuntimeException("table expected, got "+getLuaName(obj));
		return (Map) obj;
	}
	
	public static Double checkNumber(Object[] arguments, int index, String method)
	{
		if (arguments.length <= index)
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (number expected, got nil)");
		Object obj = arguments[index];
		if (!(obj instanceof Double))
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (number expected, got "+getLuaName(obj)+")");
		return toNumber(obj);
	}
	
	public static Integer checkInteger(Object[] arguments, int index, String method)
	{
		if (arguments.length <= index)
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (number expected, got nil)");
		Object obj = arguments[index];
		if (!(obj instanceof Double))
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (number expected, got "+getLuaName(obj)+")");
		return toInteger(obj);
	}
	
	public static String checkString(Object[] arguments, int index, String method)
	{
		if (arguments.length <= index)
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (string expected, got nil)");
		Object obj = arguments[index];
		if (!(obj instanceof String))
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (string expected, got "+getLuaName(obj)+")");
		return toLString(obj);
	}
	
	public static Map checkTable(Object[] arguments, int index, String method)
	{
		if (arguments.length <= index)
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (table expected, got nil)");
		Object obj = arguments[index];
		if (!(obj instanceof Map))
			throw new RuntimeException("bad argument #"+(index+1)+" to "+method+" (table expected, got "+getLuaName(obj)+")");
		return toTable(obj);
	}
	
	public static boolean isString(Object obj)
	{
		return (obj instanceof String);
	}
	
	public static boolean isNumber(Object obj)
	{
		return (obj instanceof Double);
	}
	
	public static boolean isTable(Object obj)
	{
		return (obj instanceof Map);
	}
}
