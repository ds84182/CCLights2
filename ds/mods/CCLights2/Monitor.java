package ds.mods.CCLights2;

public class Monitor {
	public GPU gpu;
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
		System.out.println("Resized to: "+w+","+h);
	}
	
	public GPU getGpu() {
		return gpu;
	}
	public void setGpu(GPU gpu) {
		this.gpu = gpu;
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
