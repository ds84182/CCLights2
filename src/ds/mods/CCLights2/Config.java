package ds.mods.CCLights2;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

public class Config {
	public static boolean DEBUGS;
	public static int Tablet;
	static int Monitor;
	static int MonitorBig;
	static int Gpu;
	static int Ram;
	static int TTrans;
	static void loadConfig(Configuration config)
	  {
	    config.load();
	    Monitor= config.get("Blocks and item ids", "Monitor", 543).getInt(543);
	    Gpu = config.get("Blocks and item ids", "Gpu", 542).getInt(542);
	    TTrans = config.get("Blocks and item ids", "TTrans", 544).getInt(544);
	    MonitorBig = config.get("Blocks and item ids", "Big Monitor", 545).getInt(545);
	    Ram = config.get("Blocks and item ids", "Ram", 4097-256).getInt(4097-256);
	    Tablet = config.get("Blocks and item ids", "Tablet", 4098).getInt(4098);
	    DEBUGS = config.get("Misc", "DEBUG", false).getBoolean(true);
	    config.save();
	  }
}
