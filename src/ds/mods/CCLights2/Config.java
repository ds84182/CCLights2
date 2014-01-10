package ds.mods.CCLights2;

import net.minecraftforge.common.Configuration;

public class Config {
	public static boolean DEBUGS;
	public static boolean Vanilla,IC2,gzip;
	public static int Tablet,light,advlight,Monitor,MonitorBig,Gpu,Ram,TTrans;
	static void loadConfig(Configuration config)
	  {
	    config.load();
	    Monitor= config.get("Blocks and item ids", "Monitor", 543).getInt(543);
	    Gpu = config.get("Blocks and item ids", "Gpu", 542).getInt(542);
	    TTrans = config.get("Blocks and item ids", "TTrans", 544).getInt(544);
	    MonitorBig = config.get("Blocks and item ids", "Big Monitor", 545).getInt(545);
	    Ram = config.get("Blocks and item ids", "Ram", 4097-256).getInt(4097-256);
	    Tablet = config.get("Blocks and item ids", "Tablet", 4098).getInt(4098);
	    light = config.get("Blocks and item ids", "Light", 546).getInt(546);
	    advlight = config.get("Blocks and item ids", "Advanced Light", 547).getInt(547);
	    DEBUGS = config.get("Misc", "DEBUG", false).getBoolean(false);
	    gzip = config.get("Misc", "GZIP", true).getBoolean(true);
	    Vanilla = config.get("Misc", "CompatVanilla", true).getBoolean(true);
	    IC2 = config.get("Misc", "CompatIC2", false).getBoolean(false);
	    config.save();
	  }
}
