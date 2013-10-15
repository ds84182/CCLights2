package ds.mods.CCLights2;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.minecraft.util.ChatAllowedCharacters;


public class Texture {
	public BufferedImage img;
	public Graphics2D graphics;
	
	private int width;
	private int height;
	
	public static BufferedImage font;
	
	private static int[] charWidth = new int[256];
	
	static int[] aint;
	
	public Texture(int w, int h)
	{
		img = new BufferedImage(w,h,2);
		graphics = img.createGraphics();
		width = w;
		height = h;
		
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
	
	public void plot(int r, int g, int b, int x, int y)
	{
		graphics.setColor(new Color(r,g,b));
		graphics.fillRect(x, y, 1, 1);
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
		AffineTransform trans = new AffineTransform();
		trans.scale(1, -1);
		graphics.drawImage(img, trans, null);
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
		graphics.clipRect(x+tx, x+ty, w, h);
		graphics.setColor(new Color(r,g,b));
		graphics.drawImage(tex.img, x, y, null);
	}
	public void fill(int r, int g, int b)
	{
		graphics.setColor(new Color(r,g,b));
		graphics.fillRect(0, 0, width, height);
	}

	public int[] getRGB(int x, int y) {
		x = x%width;
		y = y%height;
		return img.getData().getPixel(x, y, new int[4]);
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

	public void resize(int w, int h) {
		dispose();
		img = new BufferedImage(w,h,2);
		graphics = img.createGraphics();
		width = w;
		height = h;
	}
	
	public void dispose()
	{
		graphics.dispose();
		graphics = null;
		img = null;
	}
}
