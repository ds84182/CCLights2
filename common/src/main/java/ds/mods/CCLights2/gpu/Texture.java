package ds.mods.CCLights2.gpu;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import ds.mods.CCLights2.jhlabs.image.BoxBlurFilter;
import ds.mods.CCLights2.jhlabs.image.GaussianFilter;
import ds.mods.CCLights2.jhlabs.image.GlowFilter;
import ds.mods.CCLights2.jhlabs.image.UnsharpFilter;

/**
 * A CPU-side ARGB framebuffer with the drawing primitives the GPU exposes.
 * This class deliberately has no Minecraft dependencies so it can be exercised headless.
 * <p>
 * Threading: drawing happens on the ComputerCraft thread (server) or the client draw thread,
 * always under the owning GPU's lock. {@link #texUpdate()} copies the pixels into
 * {@link #getRgbCache()} under this texture's own lock, which is what the render thread reads.
 */
public class Texture {
	/**
	 * Glyph order of Minecraft's ascii.png starting at code point 32 (row 2 of the atlas).
	 * This is the 1.6-era ChatAllowedCharacters table; 1.7 no longer exposes it.
	 */
	public static final String FONT_CHARS = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_'abcdefghijklmnopqrstuvwxyz{|}~⌂"
			+ "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒ"
			+ "áíóúñÑªº¿®¬½¼¡«»";
	public static final int FONT_HEIGHT = 8;
	public static final int MAX_DIMENSION = 4096;

	private static int[] fontPixels;
	private static int fontWidth;
	private static int cellWidth;
	private static int cellHeight;
	private static final int[] charWidth = new int[256];
	private static volatile boolean fontLoaded;

	/** Pixel storage; TYPE_INT_ARGB so the raster can be accessed as an int[]. */
	private BufferedImage img;
	private Graphics2D graphics;
	private int width;
	private int height;

	private int[] rgbCache;
	private int cacheVersion = -1;
	private volatile int version = 0;
	private boolean wantCache;
	private volatile boolean disposed;

	public Texture(int w, int h) {
		allocate(w, h);
	}

	private void allocate(int w, int h) {
		w = Math.max(1, Math.min(MAX_DIMENSION, w));
		h = Math.max(1, Math.min(MAX_DIMENSION, h));
		if (graphics != null) graphics.dispose();
		img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		graphics = img.createGraphics();
		width = w;
		height = h;
		disposed = false;
		markDirty();
	}

	// ------------------------------------------------------------------ font

	/** Loads the glyph atlas (128x128, 16x16 cells) and measures character widths the way Minecraft does. */
	public static synchronized void loadFont(InputStream in) throws IOException {
		BufferedImage font = ImageIO.read(in);
		if (font == null) throw new IOException("font atlas is not an image");
		fontWidth = font.getWidth();
		int fontHeight = font.getHeight();
		fontPixels = new int[fontWidth * fontHeight];
		font.getRGB(0, 0, fontWidth, fontHeight, fontPixels, 0, fontWidth);
		cellWidth = fontWidth / 16;
		cellHeight = fontHeight / 16;
		float scale = 8.0F / cellWidth;
		for (int c = 0; c < 256; c++) {
			if (c == 32) {
				charWidth[c] = 4;
				continue;
			}
			int col = c % 16;
			int row = c / 16;
			int last = cellWidth - 1;
			outer:
			for (; last >= 0; last--) {
				int px = col * cellWidth + last;
				for (int y = 0; y < cellHeight; y++) {
					int py = row * cellHeight + y;
					if ((fontPixels[py * fontWidth + px] >>> 24) != 0) break outer;
				}
			}
			charWidth[c] = (int) (0.5D + (last + 1) * scale) + 1;
		}
		fontLoaded = true;
	}

	public static boolean isFontLoaded() {
		return fontLoaded;
	}

	/** Index of the glyph for a character in the atlas, or -1 when the atlas has no glyph for it. */
	public static int glyphIndex(char c) {
		int i = FONT_CHARS.indexOf(c);
		return i < 0 ? -1 : i + 32;
	}

	public static int getCharWidth(char c) {
		if (c == ' ') return 4;
		int g = glyphIndex(c);
		if (g < 0) g = glyphIndex('?');
		return fontLoaded ? charWidth[g] : 6;
	}

	/** Width in pixels of a string, honouring the section-sign formatting codes Minecraft uses. */
	public static int getStringWidth(String s) {
		if (s == null) return 0;
		int w = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				i++;
				continue;
			}
			w += getCharWidth(c);
		}
		return w;
	}

	// ------------------------------------------------------------------ accessors

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public BufferedImage getImage() {
		return img;
	}

	public Graphics2D getGraphics() {
		return graphics;
	}

	public boolean isDisposed() {
		return disposed;
	}

	/** Virtual memory this texture costs in the GPU's memory budget. */
	public int getMemoryUse() {
		return Math.max(1, (width * height) / 32);
	}

	public static int memoryUseFor(int w, int h) {
		return Math.max(1, (w * h) / 32);
	}

	/** Bumped whenever pixels change; used to skip unnecessary uploads. */
	public int getVersion() {
		return version;
	}

	public void markDirty() {
		version++;
	}

	private int[] pixels() {
		return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
	}

	// ------------------------------------------------------------------ render cache

	/** Monitors set this so texUpdate keeps a copy of the pixels for the render thread. */
	public synchronized void setWantCache(boolean want) {
		wantCache = want;
		if (!want) rgbCache = null;
	}

	/** Refreshes the render copy if the pixels changed since the last call. Cheap when nothing changed. */
	public synchronized void texUpdate() {
		if (!wantCache || disposed) return;
		if (cacheVersion == version && rgbCache != null && rgbCache.length == width * height) return;
		if (rgbCache == null || rgbCache.length != width * height) rgbCache = new int[width * height];
		System.arraycopy(pixels(), 0, rgbCache, 0, width * height);
		cacheVersion = version;
	}

	/** Pixels as last published by {@link #texUpdate()}; read under this texture's lock. */
	public int[] getRgbCache() {
		return rgbCache;
	}

	public int getCacheVersion() {
		return cacheVersion;
	}

	// ------------------------------------------------------------------ primitives

	private Graphics2D begin(DrawState s) {
		s.apply(graphics);
		return graphics;
	}

	private void end() {
		graphics.setTransform(DrawState.IDENTITY);
		graphics.setClip(null);
		graphics.setComposite(AlphaComposite.SrcOver);
		markDirty();
	}

	/** Fills the whole texture with a colour, ignoring transform, clip and blend mode. */
	public void fill(Color c) {
		graphics.setTransform(DrawState.IDENTITY);
		graphics.setClip(null);
		graphics.setBackground(c);
		graphics.clearRect(0, 0, width, height);
		markDirty();
	}

	/** Sets a rectangle to exactly the given colour (no blending), honouring transform and clip. */
	public void clearRect(DrawState s, int x, int y, int w, int h) {
		Graphics2D g = begin(s);
		g.setComposite(AlphaComposite.Src);
		g.fillRect(x, y, w, h);
		end();
	}

	public void plot(DrawState s, int x, int y) {
		Graphics2D g = begin(s);
		g.fillRect(x, y, 1, 1);
		end();
	}

	public void line(DrawState s, int x1, int y1, int x2, int y2) {
		Graphics2D g = begin(s);
		g.drawLine(x1, y1, x2, y2);
		end();
	}

	public void rect(DrawState s, int x, int y, int w, int h) {
		Graphics2D g = begin(s);
		g.drawRect(x, y, w - 1, h - 1);
		end();
	}

	public void filledRect(DrawState s, int x, int y, int w, int h) {
		Graphics2D g = begin(s);
		g.fillRect(x, y, w, h);
		end();
	}

	public void roundRect(DrawState s, int x, int y, int w, int h, int aw, int ah) {
		Graphics2D g = begin(s);
		g.drawRoundRect(x, y, w - 1, h - 1, aw, ah);
		end();
	}

	public void filledRoundRect(DrawState s, int x, int y, int w, int h, int aw, int ah) {
		Graphics2D g = begin(s);
		g.fillRoundRect(x, y, w, h, aw, ah);
		end();
	}

	public void oval(DrawState s, int x, int y, int w, int h) {
		Graphics2D g = begin(s);
		g.drawOval(x, y, w - 1, h - 1);
		end();
	}

	public void filledOval(DrawState s, int x, int y, int w, int h) {
		Graphics2D g = begin(s);
		g.fillOval(x, y, w, h);
		end();
	}

	public void arc(DrawState s, int x, int y, int w, int h, int start, int extent) {
		Graphics2D g = begin(s);
		g.drawArc(x, y, w - 1, h - 1, start, extent);
		end();
	}

	public void filledArc(DrawState s, int x, int y, int w, int h, int start, int extent) {
		Graphics2D g = begin(s);
		g.fillArc(x, y, w, h, start, extent);
		end();
	}

	public void polygon(DrawState s, int[] xs, int[] ys, int n) {
		Graphics2D g = begin(s);
		g.drawPolygon(xs, ys, n);
		end();
	}

	public void filledPolygon(DrawState s, int[] xs, int[] ys, int n) {
		Graphics2D g = begin(s);
		g.fillPolygon(xs, ys, n);
		end();
	}

	public void curve(DrawState s, double x1, double y1, double cx, double cy, double x2, double y2) {
		Graphics2D g = begin(s);
		g.draw(new QuadCurve2D.Double(x1, y1, cx, cy, x2, y2));
		end();
	}

	public void bezier(DrawState s, double x1, double y1, double c1x, double c1y, double c2x, double c2y, double x2, double y2) {
		Graphics2D g = begin(s);
		g.draw(new CubicCurve2D.Double(x1, y1, c1x, c1y, c2x, c2y, x2, y2));
		end();
	}

	/** Fills a rectangle with a linear gradient from the current colour to c2. */
	public void gradientRect(DrawState s, int x, int y, int w, int h, Color c2, boolean vertical) {
		Graphics2D g = begin(s);
		GradientPaint paint = vertical
				? new GradientPaint(x, y, s.color, x, y + h, c2)
				: new GradientPaint(x, y, s.color, x + w, y, c2);
		g.setPaint(paint);
		g.fillRect(x, y, w, h);
		g.setPaint(s.color);
		end();
	}

	/** Draws text with the Minecraft bitmap font, tinted by the current colour. */
	public void drawText(DrawState s, String text, int x, int y) {
		if (!fontLoaded || text == null || text.isEmpty()) return;
		int w = getStringWidth(text);
		if (w <= 0) return;
		int[] strip = new int[w * FONT_HEIGHT];
		int argb = s.color.getRGB();
		int colorAlpha = (argb >>> 24);
		int pen = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i++;
				continue;
			}
			int cw = getCharWidth(c);
			if (c != ' ') {
				int glyph = glyphIndex(c);
				if (glyph < 0) glyph = glyphIndex('?');
				int gx = (glyph % 16) * cellWidth;
				int gy = (glyph / 16) * cellHeight;
				int cols = Math.min(cellWidth, cw - 1);
				for (int fy = 0; fy < FONT_HEIGHT && fy < cellHeight; fy++) {
					int rowBase = (gy + fy) * fontWidth + gx;
					for (int fx = 0; fx < cols; fx++) {
						int a = fontPixels[rowBase + fx] >>> 24;
						if (a == 0) continue;
						int outA = a * colorAlpha / 255;
						strip[fy * w + pen + fx] = (outA << 24) | (argb & 0xFFFFFF);
					}
				}
			}
			pen += cw;
		}
		BufferedImage stripImg = new BufferedImage(w, FONT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		stripImg.setRGB(0, 0, w, FONT_HEIGHT, strip, 0, w);
		Graphics2D g = begin(s);
		g.drawImage(stripImg, x, y, null);
		end();
	}

	/** Draws another texture (or a sub-rectangle of it) at x,y, tinted and faded by the current colour. */
	public void drawTexture(DrawState s, Texture tex, int x, int y, int tx, int ty, int w, int h) {
		if (tex == null || tex.disposed) return;
		BufferedImage src = tex.subImage(tx, ty, w, h);
		if (src == null) return;
		Graphics2D g = begin(s);
		if (s.isTinted()) {
			g.drawImage(src, tintOp(s.color), x, y);
		} else {
			g.drawImage(src, x, y, null);
		}
		end();
	}

	public void drawTexture(DrawState s, Texture tex, int x, int y) {
		if (tex == null) return;
		drawTexture(s, tex, x, y, 0, 0, tex.width, tex.height);
	}

	/** Draws an ARGB buffer (row major) at x,y honouring transform, clip and blend mode but not the colour. */
	public void drawPixels(DrawState s, int[] argb, int x, int y, int w, int h) {
		BufferedImage img = blank(w, h);
		img.setRGB(0, 0, w, h, argb, 0, w);
		Graphics2D g = begin(s);
		g.drawImage(img, x, y, null);
		end();
	}

	/** Draws another texture stretched to w x h. */
	public void drawTextureScaled(DrawState s, Texture tex, int x, int y, int w, int h) {
		if (tex == null || tex.disposed || w <= 0 || h <= 0) return;
		BufferedImage src = tex.img;
		if (s.isTinted()) src = tintOp(s.color).filter(src, null);
		Graphics2D g = begin(s);
		g.drawImage(src, x, y, w, h, null);
		end();
	}

	private static RescaleOp tintOp(Color c) {
		float[] scales = { c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f };
		return new RescaleOp(scales, new float[4], null);
	}

	private BufferedImage subImage(int tx, int ty, int w, int h) {
		if (tx == 0 && ty == 0 && w == width && h == height) return img;
		int x0 = Math.max(0, tx), y0 = Math.max(0, ty);
		int x1 = Math.min(width, tx + w), y1 = Math.min(height, ty + h);
		if (x1 <= x0 || y1 <= y0) return null;
		return img.getSubimage(x0, y0, x1 - x0, y1 - y0);
	}

	// ------------------------------------------------------------------ raw pixel access

	/** ARGB value of a pixel, or 0 when outside the texture. */
	public int getRGB(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) return 0;
		return pixels()[y * width + x];
	}

	/** Copies a region as ARGB values (row major). Pixels outside the texture read as 0. */
	public int[] getPixels(int x, int y, int w, int h) {
		int[] out = new int[w * h];
		int[] px = pixels();
		for (int j = 0; j < h; j++) {
			int sy = y + j;
			if (sy < 0 || sy >= height) continue;
			for (int i = 0; i < w; i++) {
				int sx = x + i;
				if (sx < 0 || sx >= width) continue;
				out[j * w + i] = px[sy * width + sx];
			}
		}
		return out;
	}

	/** Writes ARGB values (row major) directly, ignoring transform, clip and blend mode. */
	public void setPixels(int x, int y, int w, int h, int[] argb) {
		int[] px = pixels();
		for (int j = 0; j < h; j++) {
			int dy = y + j;
			if (dy < 0 || dy >= height) continue;
			for (int i = 0; i < w; i++) {
				int dx = x + i;
				if (dx < 0 || dx >= width) continue;
				int idx = j * w + i;
				if (idx >= argb.length) return;
				px[dy * width + dx] = argb[idx];
			}
		}
		markDirty();
	}

	// ------------------------------------------------------------------ whole-texture operations

	/** Replaces the pixel storage with the given image (same size not required). */
	private void replaceImage(BufferedImage newImg) {
		graphics.dispose();
		img = newImg;
		graphics = img.createGraphics();
		width = img.getWidth();
		height = img.getHeight();
		markDirty();
	}

	private BufferedImage blank(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	public void flipVertical() {
		BufferedImage out = blank(width, height);
		Graphics2D g = out.createGraphics();
		AffineTransform t = new AffineTransform(1, 0, 0, -1, 0, height);
		g.drawImage(img, t, null);
		g.dispose();
		replaceImage(out);
	}

	public void flipHorizontal() {
		BufferedImage out = blank(width, height);
		Graphics2D g = out.createGraphics();
		AffineTransform t = new AffineTransform(-1, 0, 0, 1, width, 0);
		g.drawImage(img, t, null);
		g.dispose();
		replaceImage(out);
	}

	/** Rescales the texture contents to a new size. */
	public void resize(int w, int h, boolean smooth) {
		w = Math.max(1, Math.min(MAX_DIMENSION, w));
		h = Math.max(1, Math.min(MAX_DIMENSION, h));
		BufferedImage out = blank(w, h);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		replaceImage(out);
	}

	/** Reallocates the texture to a new size, keeping whatever content fits in the top-left corner. */
	public void reallocate(int w, int h, boolean keepContent) {
		w = Math.max(1, Math.min(MAX_DIMENSION, w));
		h = Math.max(1, Math.min(MAX_DIMENSION, h));
		BufferedImage out = blank(w, h);
		if (keepContent) {
			Graphics2D g = out.createGraphics();
			g.drawImage(img, 0, 0, null);
			g.dispose();
		}
		replaceImage(out);
	}

	/** Copies all pixels from another texture, resizing this one to match. */
	public void copyFrom(Texture other) {
		BufferedImage out = blank(other.width, other.height);
		System.arraycopy(other.pixels(), 0, ((DataBufferInt) out.getRaster().getDataBuffer()).getData(), 0, other.width * other.height);
		replaceImage(out);
	}

	public void blur(int radius) {
		BoxBlurFilter f = new BoxBlurFilter();
		f.setRadius(Math.max(1, Math.min(64, radius)));
		replaceImage(f.filter(img, blank(width, height)));
	}

	public void gaussianBlur(float radius) {
		GaussianFilter f = new GaussianFilter(Math.max(0.5f, Math.min(64f, radius)));
		replaceImage(f.filter(img, blank(width, height)));
	}

	public void glow(float amount) {
		GlowFilter f = new GlowFilter();
		f.setAmount(Math.max(0f, Math.min(4f, amount)));
		replaceImage(f.filter(img, blank(width, height)));
	}

	public void sharpen(float amount) {
		UnsharpFilter f = new UnsharpFilter();
		f.setAmount(Math.max(0f, Math.min(4f, amount)));
		replaceImage(f.filter(img, blank(width, height)));
	}

	public void invert() {
		int[] px = pixels();
		for (int i = 0; i < px.length; i++) px[i] = (px[i] & 0xFF000000) | (~px[i] & 0xFFFFFF);
		markDirty();
	}

	public void grayscale() {
		int[] px = pixels();
		for (int i = 0; i < px.length; i++) {
			int p = px[i];
			int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
			int l = (r * 299 + g * 587 + b * 114) / 1000;
			px[i] = (p & 0xFF000000) | (l << 16) | (l << 8) | l;
		}
		markDirty();
	}

	public void dispose() {
		if (disposed) return;
		disposed = true;
		if (graphics != null) graphics.dispose();
		graphics = null;
		img = null;
		rgbCache = null;
	}
}
