package ds.mods.CCLights2;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import net.minecraft.util.ChatAllowedCharacters;


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
	
	public static BufferedImage font;
	
	private static int[] charWidth = new int[256];
	
	static int[] aint;
	
	public Texture(int w, int h)
	{
		//img = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
		//graphics = img.createGraphics();
		width = w;
		height = h;
		texture = new int[w*h];
		bytedata = new int[3*w*h];
		
		if (font == null)
		{
			try {
				font = ImageIO.read(getClass().getResourceAsStream("ascii.png"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			int i = font.getWidth();
	        int j = font.getHeight();
	        aint = new int[i * j];
	        font.getRGB(0, 0, i, j, aint, 0, i);
	        int k = j / 16;
	        int l = i / 16;
	        byte b0 = 1;
	        float f = 8.0F / (float)l;
	        int i1 = 0;

	        while (i1 < 256)
	        {
	            int j1 = i1 % 16;
	            int k1 = i1 / 16;

	            if (i1 == 32)
	            {
	                this.charWidth[i1] = 3 + b0;
	            }

	            int l1 = l - 1;

	            while (true)
	            {
	                if (l1 >= 0)
	                {
	                    int i2 = j1 * l + l1;
	                    boolean flag = true;

	                    for (int j2 = 0; j2 < k && flag; ++j2)
	                    {
	                        int k2 = (k1 * l + j2) * i;

	                        if ((aint[i2 + k2] >> 24 & 255) != 0)
	                        {
	                            flag = false;
	                        }
	                    }

	                    if (flag)
	                    {
	                        --l1;
	                        continue;
	                    }
	                }

	                ++l1;
	                this.charWidth[i1] = (int)(0.5D + (double)((float)l1 * f)) + b0;
	                ++i1;
	                break;
	            }
	        }
		}
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
		return width*height/16;
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
				int i = ((ny+ty)*tex.width)+(nx+tx);
				if (i >= 0 && (!tex.isTransparent || tex.texture[i] != tex.transparentColor))
				{
					plot(
					(int)(tex.bytedata[i*3]*(r/255F)),
					(int)(tex.bytedata[i*3+1]*(g/255F)),
					(int)(tex.bytedata[i*3+2]*(b/255F)),
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
	
	public static int getCharWidth(char par1)
    {
        if (par1 == 167)
        {
            return -1;
        }
        else if (par1 == 32)
        {
            return 4;
        }
        else
        {
            int i = ChatAllowedCharacters.allowedCharacters.indexOf(par1);

            if (i >= 0)
            {
                return charWidth[i + 32];
            }
            else
            {
                return 0;
            }
        }
    }
	
	public static int getStringWidth(String par1Str)
    {
        if (par1Str == null)
        {
            return 0;
        }
        else
        {
            int i = 0;
            boolean flag = false;

            for (int j = 0; j < par1Str.length(); ++j)
            {
                char c0 = par1Str.charAt(j);
                int k = getCharWidth(c0);

                if (k < 0 && j < par1Str.length() - 1)
                {
                    ++j;
                    c0 = par1Str.charAt(j);

                    if (c0 != 108 && c0 != 76)
                    {
                        if (c0 == 114 || c0 == 82)
                        {
                            flag = false;
                        }
                    }
                    else
                    {
                        flag = true;
                    }

                    k = 0;
                }

                i += k;

                if (flag)
                {
                    ++i;
                }
            }

            return i;
        }
    }
	
	public void drawText(String text, int x, int y, int r, int g, int b)
	{
		for (int i = 0; i<text.length(); i++)
		{
			int cha = ChatAllowedCharacters.allowedCharacters.indexOf(text.charAt(i));
			if (cha == -1) cha = 0;
			//Draw character
			int cx = cha%16;
			int cy = (cha-cx)/16;
			cy*=8;
			cy+=16;
			cx*=8;
			//System.out.printf("Position of %s: %d, %d\n",text.substring(i, i+1),cx,cy);
			for (int fx = 0; fx<6; fx++)
			{
				for (int fy = 0; fy<8; fy++)
				{
					int rgb = aint[((fy+cy)*font.getWidth())+(fx+cx)];
					if ((rgb&0xFFFFFF) > 0)
					{
						plot(r,g,b,fx+x,fy+y);
					}
				}
			}
			x+=getCharWidth(text.charAt(i));
		}
	}
}
