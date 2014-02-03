package ds.mods.CCLights2;

import java.util.logging.Level;

import net.minecraftforge.common.Configuration;

public class Config {
	public static boolean DEBUGS;
	public static boolean Vanilla,IC2;
	public static Integer[] monitorSize;
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
	    Vanilla = config.get("Misc", "CompatVanilla", true).getBoolean(true);
	    IC2 = config.get("Misc", "CompatIC2", false).getBoolean(false);
	    parse(config.get("Misc", "MonitorSize", "256x144").getString());
	    config.save();
	}

	private static void parse(String monitorSizez) {
		String[] mParser = monitorSizez.split("x");
		if (mParser.length == 2) {
			monitorSize = new Integer[] { Integer.decode(mParser[0]),Integer.decode(mParser[1]) };
			if (monitorSize[0] < 1 || monitorSize[1] < 1) {
				setDefaults();
				CCLights2.logger.log(Level.WARNING,"Invalid monitor/externalmonitor size found, using defaults");
			} 
		}else {
			setDefaults();
			CCLights2.logger.log(Level.WARNING,"Invalid monitor/externalmonitor size found, using defaults");
		}
	}
	
    public static final void setDefaults(){
	 monitorSize = new Integer[]{256,144};
	}
}
