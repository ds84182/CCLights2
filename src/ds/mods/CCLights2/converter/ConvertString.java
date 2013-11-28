package ds.mods.CCLights2.converter;

public class ConvertString {
	public static String convert(Object obj) throws Exception
	{
		if (obj instanceof String)
			return (String) obj;
		else
			throw new Exception("string expected, got "+obj.getClass().getName());
	}
}
