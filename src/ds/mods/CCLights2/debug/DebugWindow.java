package ds.mods.CCLights2.debug;

import java.awt.*;

import javax.swing.JPanel;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;

public class DebugWindow {
	Frame debug;
	JPanel panel;
	Graphics graph;
    TileEntityGPU gpu;
	public DebugWindow(TileEntityGPU gpu)
	{
        this.gpu = gpu;
		debug = new Frame();
		debug.setTitle("CCLights 2 debug");
		debug.setVisible(true);
		panel = new JPanel();
        panel.setSize(gpu.gpu.textures[0].getWidth(),gpu.gpu.textures[0].getHeight());
		debug.add(panel);
        debug.setSize(gpu.gpu.textures[0].getWidth(),gpu.gpu.textures[0].getHeight());
        graph = panel.getGraphics();
        graph.drawImage(gpu.gpu.textures[0].img, 0, 0, null);
	}

    public void update()
    {
    	graph.drawImage(gpu.gpu.textures[0].img, 0, 0, null);
    }
}
