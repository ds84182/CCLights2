package ds.mods.CCLights2.gpu;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Blend modes for GPU drawing. NORMAL and REPLACE map to the standard Porter-Duff composites,
 * the others are implemented per pixel on the ARGB rasters CCLights2 textures use.
 */
public enum BlendComposite implements Composite {
	NORMAL("normal"),
	REPLACE("replace"),
	ADD("add"),
	SUBTRACT("subtract"),
	MULTIPLY("multiply"),
	SCREEN("screen"),
	DIFFERENCE("difference"),
	LIGHTEN("lighten"),
	DARKEN("darken"),
	XOR("xor");

	public final String luaName;

	BlendComposite(String luaName) {
		this.luaName = luaName;
	}

	public static BlendComposite byName(String name) {
		for (BlendComposite b : values()) {
			if (b.luaName.equalsIgnoreCase(name)) return b;
		}
		return null;
	}

	public static String names() {
		StringBuilder sb = new StringBuilder();
		for (BlendComposite b : values()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(b.luaName);
		}
		return sb.toString();
	}

	/** The java.awt composite to install on a Graphics2D for this mode. */
	public Composite toAwt() {
		switch (this) {
		case NORMAL:
			return AlphaComposite.SrcOver;
		case REPLACE:
			return AlphaComposite.Src;
		default:
			return this;
		}
	}

	@Override
	public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
		return new Context(this, srcColorModel, dstColorModel);
	}

	private static final class Context implements CompositeContext {
		private final BlendComposite mode;
		private final ColorModel srcModel;
		private final ColorModel dstModel;

		Context(BlendComposite mode, ColorModel srcModel, ColorModel dstModel) {
			this.mode = mode;
			this.srcModel = srcModel;
			this.dstModel = dstModel;
		}

		@Override
		public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
			int w = Math.min(src.getWidth(), dstIn.getWidth());
			int h = Math.min(src.getHeight(), dstIn.getHeight());
			Object srcPixel = null;
			Object dstPixel = null;
			Object outPixel = null;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					srcPixel = src.getDataElements(x, y, srcPixel);
					dstPixel = dstIn.getDataElements(x, y, dstPixel);
					int s = srcModel.getRGB(srcPixel);
					int d = dstModel.getRGB(dstPixel);
					int out = blend(mode, s, d);
					outPixel = dstModel.getDataElements(out, outPixel);
					dstOut.setDataElements(x, y, outPixel);
				}
			}
		}

		@Override
		public void dispose() {}
	}

	/** Blends a non-premultiplied ARGB source pixel onto a destination pixel. */
	static int blend(BlendComposite mode, int s, int d) {
		int sa = (s >>> 24), sr = (s >> 16) & 0xFF, sg = (s >> 8) & 0xFF, sb = s & 0xFF;
		int da = (d >>> 24), dr = (d >> 16) & 0xFF, dg = (d >> 8) & 0xFF, db = d & 0xFF;
		if (sa == 0) return d;
		int rr, rg, rb;
		switch (mode) {
		case ADD:
			rr = Math.min(255, dr + sr); rg = Math.min(255, dg + sg); rb = Math.min(255, db + sb); break;
		case SUBTRACT:
			rr = Math.max(0, dr - sr); rg = Math.max(0, dg - sg); rb = Math.max(0, db - sb); break;
		case MULTIPLY:
			rr = dr * sr / 255; rg = dg * sg / 255; rb = db * sb / 255; break;
		case SCREEN:
			rr = 255 - (255 - dr) * (255 - sr) / 255; rg = 255 - (255 - dg) * (255 - sg) / 255; rb = 255 - (255 - db) * (255 - sb) / 255; break;
		case DIFFERENCE:
			rr = Math.abs(dr - sr); rg = Math.abs(dg - sg); rb = Math.abs(db - sb); break;
		case LIGHTEN:
			rr = Math.max(dr, sr); rg = Math.max(dg, sg); rb = Math.max(db, sb); break;
		case DARKEN:
			rr = Math.min(dr, sr); rg = Math.min(dg, sg); rb = Math.min(db, sb); break;
		case XOR:
			rr = dr ^ sr; rg = dg ^ sg; rb = db ^ sb; break;
		default:
			rr = sr; rg = sg; rb = sb; break;
		}
		// Mix the blended colour into the destination by the source alpha, like SrcOver does.
		int ra = sa + da * (255 - sa) / 255;
		if (ra == 0) return 0;
		int fr = (rr * sa + dr * da * (255 - sa) / 255) / ra;
		int fg = (rg * sa + dg * da * (255 - sa) / 255) / ra;
		int fb = (rb * sa + db * da * (255 - sa) / 255) / ra;
		return (ra << 24) | (clamp(fr) << 16) | (clamp(fg) << 8) | clamp(fb);
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}
}
