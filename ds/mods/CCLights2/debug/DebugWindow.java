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
        try
        {
            for (int i = 0; i < gpu.gpu.textures[0].texture.length; i++)
            {
                int x = i%gpu.gpu.textures[0].getWidth();
                int y = (i-x)/gpu.gpu.textures[0].getWidth();
                if (Config.DEBUGS){
                System.out.println(i);
                System.out.println(gpu.gpu.textures[0].bytedata[(i*3)]);
                System.out.println(gpu.gpu.textures[0].bytedata[(i*3)+1]);
                System.out.println(gpu.gpu.textures[0].bytedata[(i*3)+2]);
                }
                graph.setColor(new Color(gpu.gpu.textures[0].bytedata[(i*3)],gpu.gpu.textures[0].bytedata[(i*3)+1],gpu.gpu.textures[0].bytedata[(i*3)+2]));
                graph.drawRect(x,y,1,1);
            }
        }
        catch (Exception e)
        {
              e.printStackTrace();
        }
	}

    public void update()
    {
        try
        {
            for (int i = 0; i < gpu.gpu.textures[0].texture.length; i++)
            {
                int x = i%gpu.gpu.textures[0].getWidth();
                int y = (i-x)/gpu.gpu.textures[0].getWidth();
               if (Config.DEBUGS){
                System.out.println(i);
                System.out.println(gpu.gpu.textures[0].bytedata[(i*3)]);
                System.out.println(gpu.gpu.textures[0].bytedata[(i*3)+1]);
                System.out.println(gpu.gpu.textures[0].bytedata[(i*3)+2]);
               }
                graph.setColor(new Color(gpu.gpu.textures[0].bytedata[(i*3)],gpu.gpu.textures[0].bytedata[(i*3)+1],gpu.gpu.textures[0].bytedata[(i*3)+2]));
                graph.drawRect(x,y,1,1);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
