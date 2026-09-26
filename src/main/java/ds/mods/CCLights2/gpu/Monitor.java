package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import dan200.computercraft.api.lua.ILuaObject;

/**
 * A screen a GPU can draw to. Its texture is slot 0 of every connected GPU.
 * One Monitor object can be shared by several tile entities (multi-block external monitors).
 */
public class Monitor {
	public final List<GPU> gpus = new ArrayList<GPU>();
	public final Texture tex;
	public ILuaObject obj;

	public Monitor(int w, int h, ILuaObject obj) {
		tex = new Texture(w, h);
		tex.setWantCache(true);
		tex.fill(Color.black);
		tex.texUpdate();
		this.obj = obj;
	}

	/** Changes the screen size. The Texture object is kept so render caches stay valid. */
	public void resize(int w, int h, boolean keepContent) {
		if (w == tex.getWidth() && h == tex.getHeight()) return;
		tex.reallocate(w, h, keepContent);
		if (!keepContent) tex.fill(Color.black);
		tex.texUpdate();
	}

	public void addGPU(GPU gpu) {
		if (!gpus.contains(gpu)) gpus.add(gpu);
		gpu.addMonitor(this);
	}

	public void removeGPU(GPU gpu) {
		gpu.removeMonitor(this);
		gpus.remove(gpu);
	}

	public void removeAllGPUs() {
		for (GPU g : new ArrayList<GPU>(gpus)) removeGPU(g);
		gpus.clear();
	}

	public int getWidth() {
		return tex.getWidth();
	}

	public int getHeight() {
		return tex.getHeight();
	}

	public Texture getTex() {
		return tex;
	}
}
