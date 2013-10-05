package ds.mods.CCLights2.converter;

public class ConvertInteger {
	public static Integer convert(Object obj)
	{
		return ConvertDouble.convert(obj).intValue();
	}
}
