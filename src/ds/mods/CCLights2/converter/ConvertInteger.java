package ds.mods.CCLights2.converter;

public class ConvertInteger {
	public static Integer convert(Object obj) throws Exception
	{
		return ConvertDouble.convert(obj).intValue();
	}
}
