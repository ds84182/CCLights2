package ds.mods.CCLights2.converter;

public class ConvertFloat {
	public static Float convert(Object obj) throws Exception
	{
		return ConvertDouble.convert(obj).floatValue();
	}
}
