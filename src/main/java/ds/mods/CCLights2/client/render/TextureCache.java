package ds.mods.CCLights2.client.render;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.gpu.Texture;
import net.minecraft.client.renderer.texture.TextureUtil;

/**
 * One OpenGL texture per CCLights2 {@link Texture} that is shown on screen. Pixels are uploaded
 * only when the texture's version changed, which is what keeps many monitors cheap to render.
 * GL names are released when the Texture is garbage collected or the world unloads.
 */
@SideOnly(Side.CLIENT)
public final class TextureCache {
	private TextureCache() {}

	private static final class Entry extends WeakReference<Texture> {
		int glId = -1;
		int width = -1;
		int height = -1;
		int version = -1;

		Entry(Texture tex) {
			super(tex, dead);
		}
	}

	private static final ReferenceQueue<Texture> dead = new ReferenceQueue<Texture>();
	private static final WeakHashMap<Texture, Entry> entries = new WeakHashMap<Texture, Entry>();
	private static final List<Entry> live = new ArrayList<Entry>();

	/** Binds the GL texture for tex, uploading new pixels if needed. Must be called on the render thread. */
	public static void bind(Texture tex) {
		reap();
		synchronized (tex) {
			Entry e = entries.get(tex);
			if (e == null) {
				e = new Entry(tex);
				e.glId = TextureUtil.glGenTextures();
				entries.put(tex, e);
				live.add(e);
			}
			int[] px = tex.getRgbCache();
			int w = tex.getWidth(), h = tex.getHeight();
			if (px == null || px.length != w * h) {
				tex.setWantCache(true);
				tex.texUpdate();
				px = tex.getRgbCache();
			}
			if (e.width != w || e.height != h) {
				TextureUtil.allocateTexture(e.glId, w, h);
				e.width = w;
				e.height = h;
				e.version = -1;
			}
			if (px != null && px.length == w * h && e.version != tex.getCacheVersion()) {
				TextureUtil.uploadTexture(e.glId, px, w, h);
				e.version = tex.getCacheVersion();
			} else {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, e.glId);
			}
		}
	}

	private static void reap() {
		Entry e;
		while ((e = (Entry) dead.poll()) != null) {
			if (e.glId >= 0) TextureUtil.deleteTexture(e.glId);
			e.glId = -1;
			live.remove(e);
		}
	}

	/** Frees every GL texture; used when leaving a world. */
	public static void releaseAll() {
		for (Entry e : live) {
			if (e.glId >= 0) TextureUtil.deleteTexture(e.glId);
			e.glId = -1;
		}
		live.clear();
		entries.clear();
		reap();
	}
}
