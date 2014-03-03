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
	
	/**
	 * Make a new Monitor.
	 * @param width,height,ILuaObject
	 */
	public Monitor(int w, int h, ILuaObject o)
	{
		width = w;
		height = h;
		tex = new Texture(w, h);
		tex.rgbCache = new int[16*32*9*32];
		tex.fill(Color.black);
		tex.texUpdate();
		obj = o;
	}
	
	/**
	 * Resize monitor.
	 * @param Width,height
	 */
	public void resize(int w, int h)
	{
		width = w;
		height = h;
		tex.resize(w, h);
		tex.rgbCache = new int[16*32*9*32];
		tex.fill(Color.black);
		tex.texUpdate();
		CCLights2.debug("Resized to: "+w+","+h);
	}
	
	/**
	 * Get gpu from gpu arraylist at index.
	 * @param index
	 */
	public GPU getGPU(int index) {
		return gpu.get(index);
	}
	/**
	 * Add a gpu to the gpu arraylist and enable connected monitors.
	 * @param gpu :  The gpu to add
	 */
	public void addGPU(GPU gpu) {
		this.gpu.add(gpu);
		gpu.addMonitor(this);
	}
	/**
	 * Remove a gpu from the gpu arraylist and disable connected monitors.
	 * @param GPU : The gpu to remove
	 */
	public void removeGPU(GPU gpu) {
		gpu.removeMonitor(this);
		this.gpu.remove(gpu);
	}
	/**
	 * Remove all gpus from the arraylist and disable connected monitors.
	 * @param GPU : The gpu to remove
	 */
	public void removeAllGPUs() {
		for (GPU g : (ArrayList<GPU>)gpu.clone())
		{
			removeGPU(g);
		}
		gpu.clear();
	}
	/**
	 * Get monitor width ( used in CC as .getSize()[0])
	 * @returns Width
	 */
	public int getWidth() {
		return width;
	}
	/**
	 * Get monitor height ( used in CC as .getSize()[1])
	 * @returns Height
	 */
	public int getHeight() {
		return height;
	}
	/**
	 * Get monitor texture
	 * @returns Texture texture
	 */
	public Texture getTex() {
		return tex;
	}
}
