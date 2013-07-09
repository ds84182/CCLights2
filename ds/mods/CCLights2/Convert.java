package ds.mods.CCLights2;

import net.minecraft.client.renderer.texture.IconRegister;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class Convert {
	public static int to256(int r, int g, int b)
	{
		return ((b&255 & 0xC0)+((g&255 & 0xE0) >> 2)+((r & 0xE0) >> 5))&0xFF;
	}
	
	public static int to16(int r, int g, int b)
	{
		return ((b>>3))|((g>>2) << 5)|((r>>3) << 11);
	}
	
	public static int toColorDepth(int r, int g, int b, int bpp)
	{
		int color = 0;
		switch(bpp)
		{
		case 1:
		{
			color = to256(r,g,b);
			break;
		}
		case 2:
		{
			color = to16(r,g,b);
            if(Config.DEBUGS){
			System.out.println("R: "+r+" G: "+g+" B: "+b+" Color: "+color);}
			break;
		}
		default:
		{
			color = b&0xFF | (g&0xFF)<<8 | (r&0xFF)<<16;
			break;
		}
		}
		
		return color;
	}
	
	public static int[] toColorDepth(int color, int bpp)
	{
		int r;
		int g;
		int b;
		
		switch(bpp)
		{
		case 1:
		{
			int[] colors = to24from256(color);
			r = colors[0];
			g = colors[1];
			b = colors[2];
			break;
		}
		case 2:
		{
			int[] colors = to24from16(color);
			r = colors[0];
			g = colors[1];
			b = colors[2];
			break;
		}
		default:
		{
			r = (color>>16)&0xFF;
			g = (color>>8)&0xFF;
			b = (color)&0xFF;
			break;
		}
		}
		
		return new int[]{r, g, b};
	}
	
	public static int[] to24from256(int color)
	{
		int r = (color << 5)&0xE0;
		//r = (r/0xE0)*255;
		int g = (color << 2)&0xE0;
		//g = (g/0xE0)*255;
		int b = (color)&0xC0;
		//b = (b/0xC0)*255;
		//System.out.println("R: "+r+" G: "+g+" B: "+b+" Color: "+color);
		return new int[]{r, g, b};
	}
	
	public static int[] to24from16(int color)
	{
		int r = (color&0xF800)>>11;
		r = r<<3;
		int g = (color&0x7E0)>>5;
		g = g<<2;
		int b = color&0x1F;
		b = b<<3;
		//System.out.println("R: "+r+" G: "+g+" B: "+b+" Color: "+color);
		return new int[]{r, g, b};
	}
	
	public static int toInt(Object var)
	{
		return ((Double) var).intValue();
	}
}
