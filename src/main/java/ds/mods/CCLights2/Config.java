package ds.mods.CCLights2;

import net.minecraftforge.common.config.Configuration;

public class Config {
	public static final String CAT_GENERAL = "general";
	public static final String CAT_MONITOR = "monitors";
	public static final String CAT_GPU = "gpu";

	public static boolean DEBUG;
	public static boolean vanillaRecipes;
	public static int monitorWidth = 256;
	public static int monitorHeight = 144;
	public static int externalMonitorMaxWidth = 16;
	public static int externalMonitorMaxHeight = 9;
	public static int externalMonitorPixelsPerBlock = 64;
	public static int tabletRange = 10;
	public static int gpuBaseMemory = 8192;
	public static int gpuRamPerStick = 1024;
	public static boolean persistMonitorContents = true;
	public static int shaderMaxOpsPerPixel = 20000;
	public static int shaderMaxPixels = 512 * 288;
	public static int shaderMaxSourceBytes = 64 * 1024;

	static void loadConfig(Configuration config) {
		config.load();
		DEBUG = config.getBoolean("debug", CAT_GENERAL, false, "Log debugging information");
		vanillaRecipes = config.getBoolean("vanillaRecipes", CAT_GENERAL, true, "Register the vanilla crafting recipes");
		tabletRange = config.getInt("tabletRange", CAT_GENERAL, 10, 1, 256, "Distance (blocks) a tablet can be from its transceiver and still show its screen");

		monitorWidth = config.getInt("monitorWidth", CAT_MONITOR, 256, 16, 2048, "Pixel width of the single-block monitor");
		monitorHeight = config.getInt("monitorHeight", CAT_MONITOR, 144, 16, 2048, "Pixel height of the single-block monitor");
		externalMonitorMaxWidth = config.getInt("externalMonitorMaxWidth", CAT_MONITOR, 16, 1, 32, "Largest external monitor width in blocks");
		externalMonitorMaxHeight = config.getInt("externalMonitorMaxHeight", CAT_MONITOR, 9, 1, 32, "Largest external monitor height in blocks");
		externalMonitorPixelsPerBlock = config.getInt("externalMonitorPixelsPerBlock", CAT_MONITOR, 64, 8, 128, "Screen pixels per external monitor block (before the monitor's setScale)");
		persistMonitorContents = config.getBoolean("persistMonitorContents", CAT_MONITOR, true, "Save screen contents with the world so they survive restarts (uses PNG-compressed NBT)");

		gpuBaseMemory = config.getInt("baseMemory", CAT_GPU, 8192, 256, 1 << 24, "Texture memory a fresh GPU has, in texture units (1 unit = 32 pixels)");
		gpuRamPerStick = config.getInt("memoryPerRamStick", CAT_GPU, 1024, 1, 1 << 20, "Texture memory one 1K RAM item adds");
		shaderMaxOpsPerPixel = config.getInt("shaderMaxOpsPerPixel", CAT_GPU, 20000, 100, 10000000, "Instruction budget per pixel for one runShader call; guards against runaway loops");
		shaderMaxPixels = config.getInt("shaderMaxPixels", CAT_GPU, 512 * 288, 1, 4096 * 4096, "Largest region (in pixels) one runShader call may cover");
		shaderMaxSourceBytes = config.getInt("shaderMaxSourceBytes", CAT_GPU, 64 * 1024, 1024, 4 * 1024 * 1024, "Largest accepted GLSL source");
		if (config.hasChanged()) config.save();
	}
}
