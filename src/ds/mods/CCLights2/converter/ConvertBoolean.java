package ds.mods.CCLights2.converter;

public class ConvertBoolean {
	public static Boolean convert(Object obj) throws Exception
	{
		if (obj instanceof Boolean)
			return (Boolean) obj;
		else
			throw new Exception("boolean expected, got "+obj.getClass().getName());
	}
}
