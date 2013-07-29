package ds.mods.CCLights2;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;


public class Texture {
	//public BufferedImage img;
	//public Graphics graphics;
	public int[] texture;
	public int[] bytedata;
	public int bpp = 1;
	
	private int width;
	private int height;
	
	public boolean isTransparent = false;
	public int transparentColor = 0;
	
	public Color transparency = new Color(255,255,255,0);
	
	public Texture(int w, int h)
	{
		//img = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
		//graphics = img.createGraphics();
		width = w;
		height = h;
		texture = new int[w*h];
		bytedata = new int[3*w*h];
	}
	
	public void setBPP(int n)
	{
		int old = bpp;
		bpp = n;
		System.out.println("Set BPP to "+bpp);
		texture = new int[width*height];
		bytedata = new int[3*width*height];
		fill(0,0,0);
		transparentColor = 0;
		isTransparent = false;
	}
	public int getWidth() {
		return width;
	}
	public int getHeight() {
		return height;
	}
	public int getMemoryUse()
	{
		return width*height*bpp;
	}
	public void setTransparent(boolean is)
	{
		isTransparent = true;
	}
	public void setTransparencyColor(int r, int g, int b)
	{
		int conv = Convert.toColorDepth(r, g, b, bpp);
		transparentColor = conv;
	}
	public void resize(int w, int h)
	{
		width = w;
		height = h;
		texture = new int[w*h];
		bytedata = new int[3*w*h];
	}
	public void plot(int r, int g, int b, int x, int y)
	{
		if (r<0 || g<0 || b<0)
		{
			return;
		}
		int conv = Convert.toColorDepth(r, g, b, bpp);
		int i = (y*width)+x;
		if (i<texture.length & x>-1 & y>-1 & x<width & y<height)
		{
			texture[i] = conv;
			int[] col = Convert.toColorDepth(conv,bpp);
			bytedata[i*3] = (col[0]);
			bytedata[i*3+1] = (col[1]);
			bytedata[i*3+2] = (col[2]);
			//img.setRGB(x, y, Convert.toColorDepth(col[0], col[1], col[2], 4));
		}
	}
	public void plot(int conv, int x, int y)
	{
		int i = (y*width)+x;
		if (i<texture.length & x>-1 & y>-1 & x<width & y<height)
		{
			texture[i] = conv;
			int[] col = Convert.toColorDepth(conv,bpp);
			bytedata[i*3] = (col[0]);
			bytedata[i*3+1] = (col[1]);
			bytedata[i*3+2] = (col[2]);
			//img.setRGB(x, y, Convert.toColorDepth(col[0], col[1], col[2], 4));
		}
	}
	public void filledRect(int r, int g, int b, int x, int y, int w, int h)
	{
		for (int nx = 0; nx<w; nx++)
		{
			for (int ny = 0; ny<h; ny++)
			{
				plot(r,g,b,x+nx,y+ny);
			}
		}
	}
	public void rect(int r, int g, int b, int x, int y, int w, int h)
	{
		line(r,g,b,x,y,x+w,y);
		line(r,g,b,x+w,y,x+w,y+h);
		line(r,g,b,x+w,y+h,x,h+y);
		line(r,g,b,x,h+y,x,y);
	}
	// draw a line from point x1,y1 into x2,y2
	public void line(int r, int g, int b, int x1, int y1, int x2, int y2) { 
		// if point x1, y1 is on the right side of point x2, y2, change them
		if ((x1 - x2) > 0) {line(r, g, b, x2, y2, x1, y1); return;}
		// test inclination of line
		// function Math.abs(y) defines absolute value y
		if (Math.abs(y2 - y1) > Math.abs(x2 - x1)) {
			// line and y axis angle is less then 45 degrees
			// that swhy go on the next procedure
			bresteepline(r, g, b, y1, x1, y2, x2); return; 
		}
		// line and x axis angle is less then 45 degrees, so x is guiding
		// auxiliary variables
		int x = x1, y = y1, sum = x2 - x1, Dx = 2 * (x2 - x1), Dy = Math.abs(2 * (y2 - y1));
		int prirastokDy = ((y2 - y1) > 0) ? 1 : -1;
		// draw line
		for (int i = 0; i <= x2 -x1; i++) {
			plot(r, g, b, x, y);
			x++;
			sum -= Dy;
			if (sum < 0) {y = y + prirastokDy; sum += Dx;}
		}
	}

	public void bresteepline(int r, int g, int b, int x3, int y3, int x4, int y4) {
		// if point x3, y3 is on the right side of point x4, y4, change them
		if ((x3 - x4) > 0) {bresteepline(r, g, b, x4, y4, x3, y3); return;}

		int x = x3, y = y3, sum = x4 - x3,	Dx = 2 * (x4 - x3), Dy = Math.abs(2 * (y4 - y3));
		int prirastokDy = ((y4 - y3) > 0) ? 1 : -1;

		for (int i = 0; i <= x4 -x3; i++) {
			plot(r, g, b, y, x);
			x++;
				sum -= Dy;
			if (sum < 0) {y = y + prirastokDy; sum += Dx;}
		}
	}
	public void flipV()
	{
		if (height%2 == 0)
		{
			Texture tmp = new Texture(width,height/2);
			tmp.setBPP(bpp);
			//Render the bottom half flipped to a texture
			for (int y=(height/2); y<height; y++)
			{
				int co = height-y-1;
				for (int x = 0; x<width; x++)
				{
					tmp.plot(texture[(y*width)+x], x, co);
				}
				//tmp.drawTexture(this, 0, co, 0, y, width, 1, 255, 255, 255);
			}
			//Render me to the bottom half to me
			for (int y=0; y<(height/2); y++)
			{
				int co = height-y-1;
				for (int x = 0; x<width; x++)
				{
					plot(texture[(y*width)+x], x, co);
				}
				//tmp.drawTexture(this, 0, co, 0, y, width, 1, 255, 255, 255);
			}
			//Render tmp to the top half of me
			drawTexture(tmp,0,0);
		}
		else
		{
			//I hate odd numbers
			int rh = height-1;
			Texture tmp = new Texture(width,rh/2);
			tmp.setBPP(bpp);
			//Render the bottom half flipped to a texture
			for (int y=height-(rh/2); y<height; y++)
			{
				int co = y-height;
				tmp.drawTexture(this, 0, co, 0, y, width, 1, 255, 255, 255);
			}
			//Render me to the bottom half to me
			for (int y=0; y<(rh/2)-1; y++)
			{
				int co = y+(rh/2)+1;
				drawTexture(this, 0, co, 0, y, width, 1, 255, 255, 255);
			}
			//Render tmp to the top half of me
			drawTexture(tmp,0,(rh/2)+1);
		}
	}
	public void drawTexture(Texture tex, int x, int y)
	{
		if (tex == null)
		{
			System.out.println("Texture to draw is null.");
			return;
		}
		drawTexture(tex,x,y,0,0,tex.width,tex.height, 255, 255, 255);
	}
	public void drawTexture(Texture tex, int x, int y, int tx, int ty, int w, int h, int r, int g, int b)
	{
		if (tex == null)
		{
			System.out.println("Texture to draw is null.");
			return;
		}
		for (int nx = 0; nx<w; nx++)
		{
			for (int ny = 0; ny<h; ny++)
			{
				int i = ((ny+tx)*tex.width)+(nx+ty);
				if (!tex.isTransparent | tex.texture[i] != tex.transparentColor)
				{
					plot(
					tex.bytedata[i*3]*(r/255),
					tex.bytedata[i*3+1]*(g/255),
					tex.bytedata[i*3+2]*(b/255),
					nx+x,
					ny+y);
				}
			}
		}
	}
	public void fill(int r, int g, int b)
	{
		int conv = Convert.toColorDepth(r, g, b, bpp);
		int[] col = Convert.toColorDepth(conv, bpp);
		for (int i = 0; i < texture.length; i++)
		{
			texture[i] = conv;
			bytedata[i*3] = (col[0]);
			bytedata[i*3+1] = (col[1]);
			bytedata[i*3+2] = (col[2]);
		}
		//graphics.setColor(new Color(col[0],col[1],col[2]));
		//graphics.drawRect(0, 0, width, height);
	}

	public int[] getRGB(int x, int y) {
		x = x%width;
		y = y%height;
		return Convert.toColorDepth(texture[(y*width)+x], bpp);
	}
}
