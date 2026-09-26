package ds.mods.CCLights2.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.gpu.Texture;

/**
 * PNG encoding of screen and texture pixels for NBT persistence and block entity update tags.
 * Minecraft-free so the round trip can be tested headless.
 */
public final class ScreenCodec {
	private ScreenCodec() {}

	/** PNG bytes of a texture's pixels, or null if encoding failed or the texture is gone. */
	@Nullable
	public static byte[] encodePng(Texture tex) {
		if (tex == null || tex.isDisposed() || tex.getImage() == null) return null;
		return encodePng(tex.getImage());
	}

	@Nullable
	public static byte[] encodePng(BufferedImage img) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(img, "png", out)) return null;
			return out.toByteArray();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Decodes PNG (or any ImageIO format) bytes; null on failure. */
	@Nullable
	public static BufferedImage decode(@Nullable byte[] data) {
		if (data == null || data.length == 0) return null;
		try {
			return ImageIO.read(new ByteArrayInputStream(data));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Builds a new texture holding exactly the decoded pixels; null on failure. */
	@Nullable
	public static Texture decodeTexture(@Nullable byte[] data) {
		BufferedImage img = decode(data);
		if (img == null) return null;
		Texture t = new Texture(img.getWidth(), img.getHeight());
		copyInto(t, img);
		return t;
	}

	/**
	 * Copies an image's pixels exactly (no blending) into the top-left corner of a texture; whatever
	 * does not fit is cut off. Publishes the result to the render cache.
	 */
	public static void copyInto(Texture tex, BufferedImage img) {
		int w = Math.min(img.getWidth(), tex.getWidth());
		int h = Math.min(img.getHeight(), tex.getHeight());
		if (w <= 0 || h <= 0) return;
		int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		tex.setPixels(0, 0, w, h, px);
		tex.texUpdate();
	}
}
