package ds.mods.CCLights2;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ds.mods.CCLights2.client.CCLights2Client;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketProcessor;
import ds.mods.CCLights2.platform.Platform;
import ds.mods.CCLights2.platform.Services;

/**
 * Loader-neutral entry point. The Fabric entrypoints and the Forge {@code @Mod} class call
 * {@link #init()} once during mod construction and {@link #initClient()} once on the client.
 */
public final class CCLights2 {
	public static final String MOD_ID = "cclights";
	public static final Logger LOGGER = LoggerFactory.getLogger("CCLights2");

	private CCLights2() {}

	/** Common setup: config, content registration, networking, peripherals. */
	public static void init() {
		Platform platform = Services.PLATFORM;
		Config.load(platform.getConfigDir());
		GPU.shaderMaxOps = Config.shaderMaxOpsPerPixel;
		GPU.shaderMaxPixels = Config.shaderMaxPixels;
		GPU.shaderMaxSource = Config.shaderMaxSourceBytes;
		loadFont();

		Registration.register();
		platform.registerNetworkReceivers(PacketProcessor::handleServer, PacketProcessor::handleClient);
		platform.registerPeripheralProviders();
	}

	/** Client setup: renderers, client tick hook. Called after {@link #init()}, on the client only. */
	public static void initClient() {
		CCLights2Client.init();
	}

	/** The server rasterises text too, so the font is read from the jar rather than the resource manager. */
	private static void loadFont() {
		InputStream in = CCLights2.class.getResourceAsStream("/assets/cclights/textures/gui/ascii.png");
		if (in == null) {
			LOGGER.error("Font atlas assets/cclights/textures/gui/ascii.png is missing; drawText will do nothing");
			return;
		}
		try (InputStream stream = in) {
			Texture.loadFont(stream);
		} catch (IOException e) {
			LOGGER.error("Failed to load the CCLights2 font atlas", e);
		}
	}

	public static void debug(String msg) {
		if (Config.DEBUG) LOGGER.info(msg);
	}
}
