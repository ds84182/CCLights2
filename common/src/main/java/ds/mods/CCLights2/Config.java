package ds.mods.CCLights2;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Mod configuration, stored as {@code config/cclights2.json}. Same keys and defaults as the 1.7.10
 * Forge config (grouped into general / monitors / gpu). Missing or invalid values fall back to the
 * default and the file is rewritten with every key, so new options show up after an update.
 */
public final class Config {
	public static final String FILE_NAME = "cclights2.json";
	public static final String CAT_GENERAL = "general";
	public static final String CAT_MONITOR = "monitors";
	public static final String CAT_GPU = "gpu";

	public static boolean DEBUG = false;
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

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private Config() {}

	/** Reads {@code <configDir>/cclights2.json} (creating it if missing) and writes it back complete. */
	public static void load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		JsonObject root = new JsonObject();
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(r);
				if (parsed != null && parsed.isJsonObject()) root = parsed.getAsJsonObject();
				else CCLights2.LOGGER.warn("{} is not a JSON object; using defaults", file);
			} catch (Exception e) {
				CCLights2.LOGGER.warn("Could not read {}; using defaults: {}", file, e.toString());
				root = new JsonObject();
			}
		}

		Section general = new Section(root, CAT_GENERAL);
		DEBUG = general.bool("debug", false, "Log debugging information");
		tabletRange = general.integer("tabletRange", 10, 1, 256, "Distance (blocks) a tablet can be from its transceiver and still show its screen");

		Section monitors = new Section(root, CAT_MONITOR);
		monitorWidth = monitors.integer("monitorWidth", 256, 16, 2048, "Pixel width of the single-block monitor");
		monitorHeight = monitors.integer("monitorHeight", 144, 16, 2048, "Pixel height of the single-block monitor");
		externalMonitorMaxWidth = monitors.integer("externalMonitorMaxWidth", 16, 1, 32, "Largest external monitor width in blocks");
		externalMonitorMaxHeight = monitors.integer("externalMonitorMaxHeight", 9, 1, 32, "Largest external monitor height in blocks");
		externalMonitorPixelsPerBlock = monitors.integer("externalMonitorPixelsPerBlock", 64, 8, 128, "Screen pixels per external monitor block (before the monitor's setScale)");
		persistMonitorContents = monitors.bool("persistMonitorContents", true, "Save screen contents with the world so they survive restarts (uses PNG-compressed NBT)");

		Section gpu = new Section(root, CAT_GPU);
		gpuBaseMemory = gpu.integer("baseMemory", 8192, 256, 1 << 24, "Texture memory a fresh GPU has, in texture units (1 unit = 32 pixels)");
		gpuRamPerStick = gpu.integer("memoryPerRamStick", 1024, 1, 1 << 20, "Texture memory one 1K RAM item adds");
		shaderMaxOpsPerPixel = gpu.integer("shaderMaxOpsPerPixel", 20000, 100, 10000000, "Instruction budget per pixel for one runShader call; guards against runaway loops");
		shaderMaxPixels = gpu.integer("shaderMaxPixels", 512 * 288, 1, 4096 * 4096, "Largest region (in pixels) one runShader call may cover");
		shaderMaxSourceBytes = gpu.integer("shaderMaxSourceBytes", 64 * 1024, 1024, 4 * 1024 * 1024, "Largest accepted GLSL source");

		JsonObject out = new JsonObject();
		out.add(CAT_GENERAL, general.out);
		out.add(CAT_MONITOR, monitors.out);
		out.add(CAT_GPU, gpu.out);
		try {
			Files.createDirectories(configDir);
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(out, w);
			}
		} catch (IOException e) {
			CCLights2.LOGGER.warn("Could not write {}: {}", file, e.toString());
		}
	}

	/**
	 * One category of the file. JSON has no comments, so each key gets a sibling "_comment_key"
	 * entry describing it (and its range); those are ignored when reading.
	 */
	private static final class Section {
		final JsonObject in;
		final JsonObject out = new JsonObject();

		Section(JsonObject root, String name) {
			JsonElement e = root.get(name);
			in = e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		}

		boolean bool(String key, boolean def, String comment) {
			boolean v = def;
			JsonElement e = in.get(key);
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) v = e.getAsBoolean();
			else if (e != null) CCLights2.LOGGER.warn("Config {}: expected true/false, using {}", key, def);
			out.addProperty("_comment_" + key, comment + " (default " + def + ")");
			out.addProperty(key, v);
			return v;
		}

		int integer(String key, int def, int min, int max, String comment) {
			int v = def;
			JsonElement e = in.get(key);
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
				double d = e.getAsDouble();
				if (d < min || d > max || d != Math.floor(d)) {
					CCLights2.LOGGER.warn("Config {}={} is outside [{}, {}]; clamped", key, e.getAsString(), min, max);
				}
				v = (int) Math.max(min, Math.min(max, Math.floor(d)));
			} else if (e != null) {
				CCLights2.LOGGER.warn("Config {}: expected a number, using {}", key, def);
			}
			out.addProperty("_comment_" + key, comment + " (default " + def + ", range " + min + ".." + max + ")");
			out.addProperty(key, v);
			return v;
		}
	}
}
