package ds.mods.CCLights2;

import java.util.ArrayList;

public class Monitor {
	public ArrayList<GPU> gpu = new ArrayList<GPU>();
	public Texture tex;
	
	private int width;
	private int height;
	
	public Monitor(int w, int h)
	{
		width = w;
		height = h;
		tex = new Texture(w, h);
	}
	
	public void resize(int w, int h)
	{
		width = w;
		height = h;
		tex.resize(w, h);
		if (Config.DEBUGS){
		System.out.println("Resized to: "+w+","+h);}
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
