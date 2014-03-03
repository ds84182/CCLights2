package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.minecraft.util.ChatAllowedCharacters;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.jhlabs.image.BoxBlurFilter;


public class Texture {
	/**
	 * BufferedImage for image stuff
	 */
	public BufferedImage img;
	/**
	 * Graphics2D context for drawing
	 */
	public Graphics2D graphics;
	
	/**
	 * Texture width
	 */
	private int width;
	/**
	 * Texture height
	 */
	private int height;
	/**
	 * Renderlock for non flickery rendering
	 */
	public boolean renderLock = false;
	
	/**
	 * BufferedImage for font
	 */
	public static BufferedImage font;
	
	/**
	 * Transformation for render
	 */
	public AffineTransform transform = new AffineTransform();
	/**
	 * The transformation to reset to
	 */
	public static AffineTransform resetTransform = new AffineTransform();
	/**
	 * Temporary 512x512 texture
	 */
	public static Texture temp;
	
	/**
	 * Character widths for text rendering
	 */
	private static int[] charWidth = new int[256];
	
	/**
	 * Font data
	 */
	static int[] aint;
	
	/**
	 * Cache for monitors to remove render lag
	 */
	public int[] rgbCache;
	
	/**
	 * Default constructor for Texture
	 * @param w
	 * @param h
	 */
	public Texture(int w, int h)
	{
		img = new BufferedImage(w,h,2);
		graphics = img.createGraphics();
		width = w;
		height = h;
		
		if (font == null)
		{
			try {
				font = ImageIO.read(CCLights2.class.getResourceAsStream("/assets/cclights/textures/gui/ascii.png"));
			} catch (IOException e) {
				CCLights2.debug("failed to load typeface for cclights2 ;_; did you mess with the files?");
			}
			temp = new Texture(512, 512);
			int i = font.getWidth();
	        int j = font.getHeight();
	        aint = new int[i * j];
	        font.getRGB(0, 0, i, j, aint, 0, i);
	        int k = j / 16;
	        int l = i / 16;
	        byte b0 = 1;
	        float f = 8.0F / l;
	        int i1 = 0;

	        while (i1 < 256)
	        {
	            int j1 = i1 % 16;
	            int k1 = i1 / 16;

	            if (i1 == 32)
	            {
	                Texture.charWidth[i1] = 3 + b0;
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
	                Texture.charWidth[i1] = (int)(0.5D + l1 * f) + b0;
	                ++i1;
	                break;
	            }
	        }
		}
	}
	
	/**
	 * Returns the width of the texture
	 * @return The width of the texture
	 */
	public int getWidth() {
		return width;
	}
	
	/**
	 * Returns the height of the texture
	 * @return The height of the texture
	 */
	public int getHeight() {
		return height;
	}
	
	/**
	 * Returns the amount of virtual memory the texture uses
	 * @return The used memory in bytes
	 */
	public int getMemoryUse()
	{
		return (width*height)/32;
	}
	
	/**
	 * Plots a single pixel on the screen of the Color c
	 * @param c
	 * @param x
	 * @param y
	 */
	public void plot(Color c, int x, int y)
	{
		graphics.setTransform(transform);
		graphics.setColor(c);
		graphics.fillRect(x, y, 1, 1);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * Creates a filled rectangle at x and y with a width and height of w and h
	 * @param c
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public void filledRect(Color c, int x, int y, int w, int h)
	{
		graphics.setTransform(transform);
		graphics.setColor(c);
		graphics.fillRect(x, y, w, h);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * Creates a outlined rectangle at x and y with a width and height of w and h
	 * @param c
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public void rect(Color c, int x, int y, int w, int h)
	{
		graphics.setTransform(transform);
		graphics.setColor(c);
		graphics.drawRect(x, y, w, h);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * draw a line from point x1,y1 into x2,y2
	 * @param c
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 */
	public void line(Color c, int x1, int y1, int x2, int y2) { 
		graphics.setTransform(transform);
		graphics.setColor(c);
		graphics.drawLine(x1, y1, x2, y2);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * Flip the texture vertically (this may not work at all)
	 */
	public void flipV()
	{
		AffineTransform trans = new AffineTransform();
		trans.scale(1, -1);
		graphics.drawImage(img, trans, null);
	}
	
	/**
	 * Draws a texture on the graphics canvas
	 * @param tex
	 * @param x
	 * @param y
	 * @param c
	 */
	public void drawTexture(Texture tex, int x, int y, Color c)
	{
		if (tex == null)
		{
			CCLights2.debug("Texture to draw is null.");
			return;
		}
		drawTexture(tex,x,y,0,0,tex.width,tex.height, c);
	}
	
	/**
	 * Draws a texture on the screen using some weird resize thing
	 * @param tex
	 * @param x
	 * @param y
	 * @param tx
	 * @param ty
	 * @param w
	 * @param h
	 * @param c
	 */
	public void drawTexture(Texture tex, int x, int y, int tx, int ty, int w, int h, Color c)
	{
		if (tex == null)
		{
			CCLights2.debug("Texture to draw is null.");
			return;
		}
		graphics.setTransform(transform);
		graphics.setClip(new Rectangle(x, y, w, h));
		float[] scales = new float[]{c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f};//rgba
		float[] offsets = new float[4];
		RescaleOp rop = new RescaleOp(scales, offsets, null);
		graphics.drawImage(tex.img, rop, x-tx, y-ty);
		graphics.setClip(null);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * Fill the screen with the Color c
	 * @param c
	 */
	public void fill(Color c)
	{
		graphics.setBackground(c);
		graphics.clearRect(0, 0, width, height);
	}
	
	/**
	 * Draw a polygon with the Color c
	 * @param xPoints
	 * @param yPoints
	 * @param nPoints
	 * @param c
	 */
	public void polygon(int[] xPoints, int[] yPoints, int nPoints, Color c)
	{
		graphics.setColor(c);
		graphics.setTransform(transform);
		graphics.drawPolyline(xPoints, yPoints, nPoints);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * Draw a filled polygon with the Color c
	 * @param xPoints
	 * @param yPoints
	 * @param nPoints
	 * @param c
	 */
	public void filledPolygon(int[] xPoints, int[] yPoints, int nPoints, Color c)
	{
		graphics.setColor(c);
		graphics.setTransform(transform);
		graphics.drawPolygon(xPoints, yPoints, nPoints);
		graphics.setTransform(resetTransform);
	}
	
	/**
	 * Get the RGB value at pixel x and y
	 * @param x
	 * @param y
	 * @return A int array full of RGBA values
	 */
	public int[] getRGB(int x, int y) {
		x = x%width;
		y = y%height;
		return img.getData().getPixel(x, y, new int[4]);
	}
	
	/**
	 * Returns the width of the character
	 * @param par1
	 * @return the width of the character
	 */
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
                return 8;
            }
        }
    }
	
	/**
	 * Returns the width of the string
	 * @param par1Str
	 * @return the width of the string
	 */
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
	
	/**
	 * Draws the String text on the screen at x and y with the Color c
	 * @param text
	 * @param x
	 * @param y
	 * @param c
	 */
	public void drawText(String text, int x, int y, Color c)
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
						plot(c,fx+x,fy+y);
					}
				}
			}
			x+=getCharWidth(text.charAt(i));
		}
	}

	/**
	 * Resize the texture for awesome stuff
	 * @param w
	 * @param h
	 */
	public void resize(int w, int h) {
		dispose();
		img = new BufferedImage(w,h,2);
		graphics = img.createGraphics();
		width = w;
		height = h;
	}
	
	/**
	 * Dispose of the thinggy when it is needed and stuff
	 */
	@Override
	protected void finalize() throws Throwable {
		CCLights2.debug("Texture is being discarded...");
		dispose();
	}

	/**
	 * Dispose of the thinggy when it is needed and stuff
	 */
	public void dispose()
	{
		graphics.dispose();
		graphics = null;
		img = null;
	}
	
	/**
	 * Clears a rectangle to the exact Color of c
	 * @param c
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public void clearRect(Color c, int x, int y, int w, int h)
	{
		graphics.setBackground(c);
		graphics.clearRect(x, y, w, h);
	}
	
	/**
	 * Box blur filter and initalizer for awesome stuff
	 */
	public static BoxBlurFilter filter = new BoxBlurFilter();
	{
		filter.setRadius(2);
	}
	/**
	 * Perform a box blur on the texture
	 */
	public void blur() {
		filter.filter(img, img);
	}
	
	/**
	 * Update the texture content if rgbCache is initialized
	 */
	public void texUpdate()
	{
		if (rgbCache != null)
		{
			img.getRGB(0, 0, width, height, rgbCache, 0, 16*32);
		}
	}
}
