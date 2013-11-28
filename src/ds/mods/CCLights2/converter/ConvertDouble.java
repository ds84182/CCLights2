package ds.mods.CCLights2.converter;

public class ConvertDouble {
	public static Double convert(Object obj) throws Exception
	{
		if (obj instanceof Double)
			return (Double) obj;
		else
			throw new Exception("double expected, got "+obj.getClass().getName());
	}
}
