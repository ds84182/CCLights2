package ds.mods.CCLights2.converter;

public class ConvertFloat {
	public static Float convert(Object obj)
	{
		return ConvertDouble.convert(obj).floatValue();
	}
}
