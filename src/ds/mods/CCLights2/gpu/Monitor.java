package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.util.ArrayList;

import dan200.computer.api.ILuaObject;
import ds.mods.CCLights2.CCLights2;

public class Monitor {
	public ArrayList<GPU> gpu = new ArrayList<GPU>();
	public Texture tex;
	
	private int width;
	private int height;
	public ILuaObject obj;
	
	public Monitor(int w, int h, ILuaObject o)
	{
		width = w;
		height = h;
		tex = new Texture(w, h);
		tex.rgb = new int[16*32*9*32];
		tex.fill(Color.black);
		tex.texUpdate();
		obj = o;
	}
	
	public void resize(int w, int h)
	{
		width = w;
		height = h;
		tex.resize(w, h);
		tex.rgb = new int[16*32*9*32];
		tex.fill(Color.black);
		tex.texUpdate();
		CCLights2.debug("Resized to: "+w+","+h);
	}
	
	public GPU getGPU(int index) {
		return gpu.get(index);
	}
	public void addGPU(GPU gpu) {
		this.gpu.add(gpu);
		gpu.addMonitor(this);
	}
	public void removeGPU(GPU gpu) {
		gpu.removeMonitor(this);
		this.gpu.remove(gpu);
	}
	@SuppressWarnings("unchecked")
	public void removeAllGPUs() {
		for (GPU g : (ArrayList<GPU>)gpu.clone())
		{
			removeGPU(g);
		}
	}
	public int getWidth() {
		return width;
	}
	public int getHeight() {
		return height;
	}
	public Texture getTex() {
		return tex;
	}
}
