package ds.mods.CCLights2.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import cpw.mods.fml.common.registry.LanguageRegistry;

public class LangHelper {
	public static final HashMap<String, String> resources;
	private static boolean addLocalization = false;
	
	static {
		resources = new HashMap<String, String>();
		
		// Format: Lang, File
		resources.put("en_US", "/lang/en_US.lang");
	}
	
	public static void addLocalization()
	{
		if(!addLocalization) {
			addLocalization = true;
			
			Iterator<Entry<String, String>> it = resources.entrySet().iterator();
			while(it.hasNext())
			{
				Entry<String, String> set = it.next();
				LanguageRegistry.instance().loadLocalization(set.getValue(), set.getKey(), isXML(set.getValue()));
			}
		}
	}
	
	private static boolean isXML(String langFile){
		return langFile.endsWith(".xml");
	}
}
