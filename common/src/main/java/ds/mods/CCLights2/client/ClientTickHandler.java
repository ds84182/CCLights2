package ds.mods.CCLights2.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.network.PacketSenders;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * End-of-client-tick work: resets client caches when the level changes, and takes the tablet camera
 * picture. A requested picture hides the HUD and switches to first person for a couple of ticks so
 * the frames rendered in between are clean, then reads the main render target, downscales it to at
 * most {@value #MAX_W}x{@value #MAX_H} and sends it to the transceiver as PNG.
 */
public final class ClientTickHandler {
	private ClientTickHandler() {}

	public static final int MAX_W = 512;
	public static final int MAX_H = 288;
	/** Ticks the HUD stays hidden before the capture, so at least one clean frame gets rendered. */
	private static final int SETTLE_TICKS = 2;

	/** Transceiver the next camera image is for; set by {@link TabletLink#requestScreenshot}. */
	@Nullable
	public static volatile TabletTransceiverBlockEntity pendingScreenshot;

	private static int settle = -1;
	private static boolean savedHideGui;
	@Nullable
	private static CameraType savedCamera;
	@Nullable
	private static ClientLevel lastLevel;

	public static void requestScreenshot(TabletTransceiverBlockEntity tile) {
		pendingScreenshot = tile;
	}

	public static void onClientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			restoreView(mc);
			pendingScreenshot = null;
			CCLights2Client.onLevelChanged(mc.level == null);
		}
		tickScreenshot(mc);
	}

	private static void tickScreenshot(Minecraft mc) {
		TabletTransceiverBlockEntity tile = pendingScreenshot;
		if (tile == null) {
			restoreView(mc);
			return;
		}
		if (mc.level == null || mc.player == null || tile.isRemoved()) {
			pendingScreenshot = null;
			restoreView(mc);
			return;
		}
		Options options = mc.options;
		if (settle < 0) {
			savedHideGui = options.hideGui;
			savedCamera = options.getCameraType();
			options.hideGui = true;
			options.setCameraType(CameraType.FIRST_PERSON);
			settle = SETTLE_TICKS;
			return;
		}
		if (--settle > 0) return;

		pendingScreenshot = null;
		byte[] png = null;
		try {
			png = capturePng(mc);
		} catch (Exception e) {
			CCLights2.LOGGER.warn("Tablet camera capture failed", e);
		} finally {
			restoreView(mc);
		}
		if (png == null) {
			TabletLink.say(Component.translatableWithFallback("chat.cclights.tablet.capture_failed", "Could not capture the camera image."));
			return;
		}
		PacketSenders.sendScreenshot(tile, png);
		BlockPos pos = tile.getBlockPos();
		TabletLink.say(Component.translatable("chat.cclights.tablet.image_sent", pos.getX(), pos.getY(), pos.getZ()));
	}

	private static void restoreView(Minecraft mc) {
		if (settle < 0) return;
		settle = -1;
		mc.options.hideGui = savedHideGui;
		if (savedCamera != null) mc.options.setCameraType(savedCamera);
		savedCamera = null;
	}

	/** Reads the last rendered frame, downscaled to fit {@link #MAX_W}x{@link #MAX_H}, as PNG bytes. */
	@Nullable
	private static byte[] capturePng(Minecraft mc) throws java.io.IOException {
		try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
			int w = full.getWidth(), h = full.getHeight();
			if (w <= 0 || h <= 0) return null;
			double scale = Math.min(1.0, Math.min((double) MAX_W / w, (double) MAX_H / h));
			int tw = Math.max(1, (int) Math.round(w * scale));
			int th = Math.max(1, (int) Math.round(h * scale));
			if (tw == w && th == h) return full.asByteArray();
			try (NativeImage small = new NativeImage(tw, th, false)) {
				full.resizeSubRectTo(0, 0, w, h, small);
				return small.asByteArray();
			}
		}
	}
}
