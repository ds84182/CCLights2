package ds.mods.CCLights2.client;

import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.awt.Color;

import ds.mods.CCLights2.Texture;

public ClientTexture extends Texture
{
	public BufferedImage img;
	public Graphics g;
	
	public ClientTexture(int w, int h)
	{
		super(w,h);
		img = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
		g = img.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0,0,w,h);
	}
}
