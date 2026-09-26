package ds.mods.CCLights2.client;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.gpu.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * One {@link DynamicTexture} per CCLights2 {@link Texture} that is shown on screen, registered with the
 * TextureManager as {@code cclights:screen/<n>}. Pixels are re-uploaded only when the texture's
 * cache version changed, which keeps many idle monitors cheap. Entries are dropped (and their GL
 * textures freed) when the Texture is garbage collected, and all of them when the client level changes.
 * <p>
 * Everything here runs on the render thread; {@link #get(Texture)} defers itself when called elsewhere.
 */
public final class TextureCache {
	private TextureCache() {}

	private static final class Entry extends WeakReference<Texture> {
		final int id;
		final ResourceLocation location;
		DynamicTexture dynamic;
		int width = -1;
		int height = -1;
		int version = Integer.MIN_VALUE;
		boolean registered;

		Entry(Texture tex, int id) {
			super(tex, DEAD);
			this.id = id;
			this.location = new ResourceLocation(CCLights2.MOD_ID, "screen/" + id);
		}
	}

	private static final ReferenceQueue<Texture> DEAD = new ReferenceQueue<Texture>();
	private static final WeakHashMap<Texture, Entry> ENTRIES = new WeakHashMap<Texture, Entry>();
	private static final List<Entry> LIVE = new ArrayList<Entry>();
	/** Ids of released entries, reused so the set of ResourceLocations (and RenderType.text memo) stays bounded. */
	private static final ArrayDeque<Integer> FREE_IDS = new ArrayDeque<Integer>();
	private static int nextId = 0;

	/**
	 * The texture location to draw {@code tex} with, uploading new pixels first if they changed.
	 * Returns null when there is nothing to show yet. Call on the render thread.
	 */
	@Nullable
	public static ResourceLocation get(@Nullable Texture tex) {
		if (tex == null || tex.isDisposed()) return null;
		if (!RenderSystem.isOnRenderThread()) {
			RenderSystem.recordRenderCall(() -> get(tex));
			Entry e = ENTRIES.get(tex);
			return e != null && e.registered ? e.location : null;
		}
		reap();
		Entry e = ENTRIES.get(tex);
		if (e == null) {
			Integer free = FREE_IDS.poll();
			e = new Entry(tex, free != null ? free : nextId++);
			ENTRIES.put(tex, e);
			LIVE.add(e);
		}
		update(e, tex);
		return e.registered ? e.location : null;
	}

	private static void update(Entry e, Texture tex) {
		NativeImage image;
		synchronized (tex) {
			int w = tex.getWidth(), h = tex.getHeight();
			int[] px = tex.getRgbCache();
			if (px == null || px.length != w * h) {
				tex.setWantCache(true);
				tex.texUpdate();
				px = tex.getRgbCache();
			}
			if (px == null || px.length != w * h) return;
			int version = tex.getCacheVersion();
			if (e.dynamic != null && e.width == w && e.height == h && e.version == version) return;

			if (e.dynamic == null || e.width != w || e.height != h) {
				// New size: a fresh DynamicTexture; register() closes the previous one under the same location.
				e.dynamic = new DynamicTexture(w, h, false);
				e.width = w;
				e.height = h;
				Minecraft.getInstance().getTextureManager().register(e.location, e.dynamic);
				e.registered = true;
			}
			image = e.dynamic.getPixels();
			if (image == null) return;
			copyArgb(px, w, h, image);
			e.version = version;
		}
		e.dynamic.upload();
	}

	/** ARGB ints to NativeImage's ABGR layout; screens are always drawn opaque. */
	private static void copyArgb(int[] px, int w, int h, NativeImage image) {
		int i = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++, i++) {
				int argb = px[i];
				int abgr = 0xFF000000 | ((argb & 0xFF) << 16) | (argb & 0xFF00) | ((argb >> 16) & 0xFF);
				image.setPixelRGBA(x, y, abgr);
			}
		}
	}

	private static void reap() {
		Entry e;
		while ((e = (Entry) DEAD.poll()) != null) {
			release(e);
			LIVE.remove(e);
		}
	}

	private static void release(Entry e) {
		if (e.registered) {
			Minecraft.getInstance().getTextureManager().release(e.location);
			FREE_IDS.add(e.id);
		}
		e.registered = false;
		e.dynamic = null;
	}

	/** Frees every screen texture; used when the client level changes or is left. Render thread only. */
	public static void releaseAll() {
		if (!RenderSystem.isOnRenderThread()) {
			RenderSystem.recordRenderCall(TextureCache::releaseAll);
			return;
		}
		for (Entry e : LIVE) release(e);
		LIVE.clear();
		ENTRIES.clear();
		reap();
	}
}
