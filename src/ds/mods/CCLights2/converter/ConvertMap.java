package ds.mods.CCLights2.converter;

import java.util.Map;

public class ConvertMap {
	public static Map<?, ?> convert(Object obj) throws Exception
	{
		if (obj instanceof Map)
			return (Map<?, ?>) obj;
		else
			throw new Exception("table expected, got "+obj.getClass().getName());
	}
}
