package ds.mods.CCLights2.debug;

import java.awt.Frame;
import java.awt.Graphics;

import ds.mods.CCLights2.block.tileentity.TileEntityGPU;

@SuppressWarnings("serial")
public class DebugWindow extends Frame {
    TileEntityGPU gpu;
	public DebugWindow(TileEntityGPU gpu)
	{
        this.gpu = gpu;
		setTitle("CCLights 2 debug");
		setVisible(true);
        setSize(gpu.gpu.textures[0].getWidth(),gpu.gpu.textures[0].getHeight());
	}
	@Override
	public void paintAll(Graphics g) {
		g.drawImage(gpu.gpu.textures[0].img, 0, 0, null);
		super.paintAll(g);
	}
}
