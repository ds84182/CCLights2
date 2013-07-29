package ds.mods.CCLights2.gpu;

import java.util.Map;

import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.converter.ConvertInteger;
import ds.mods.CCLights2.utils.LuaMethod;
import ds.mods.CCLights2.utils.LuaMethod.Type;

public class Methods {
	
	@LuaMethod(name = "fill", ret = Type.NULL, networked = true, args = {Type.INT,Type.INT,Type.INT})
	public void fill(GPU gpu, int r, int g, int b)
	{
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.fill(r, g, b);
		}
	}
	
	@LuaMethod(name = "createTexture", ret = Type.INT, networked = true, args = {Type.INT,Type.INT})
	public int createTexture(GPU gpu, int x, int y)
	{
		return gpu.newTexture(x, y);
	}
	
	@LuaMethod(name = "getFreeMemory", ret = Type.INT)
	public int getFreeMemory(GPU gpu)
	{
		return gpu.getFreeMemory();
	}
	
	@LuaMethod(name = "getUsedMemory", ret = Type.INT)
	public int getUsedMemory(GPU gpu)
	{
		return gpu.getUsedMemory();
	}
	
	@LuaMethod(name = "getTotalMemory", ret = Type.INT)
	public int getTotalMemory(GPU gpu)
	{
		return gpu.maxmem;
	}
	
	@LuaMethod(name = "bindTexture", ret = Type.NULL, args = {Type.INT}, networked = true)
	public void bindTexture(GPU gpu, int tex) throws Exception
	{
		gpu.bindTexture(tex);
	}
	
	@LuaMethod(name = "plot", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.INT}, networked = true)
	public void plot(GPU gpu, int x, int y, int r, int g, int b) throws Exception
	{
		if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255)
		{
			throw new Exception("Invalid RGB");
		}
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.plot(r, g, b, x, y);
		}
	}
	
	@LuaMethod(name = "setColorRGB", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.INT}, networked = true)
	public void setColorRGB(GPU gpu, int x, int y, int r, int g, int b) throws Exception
	{
		if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255)
		{
			throw new Exception("Invalid RGB");
		}
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.plot(r, g, b, x, y);
		}
	}
	
	@LuaMethod(name = "drawTexture", ret = Type.NULL, args = {Type.OBJECTS}, networked = true)
	public void drawTexture(GPU gpu, Object[] args) throws Exception
	{
		int texid = -1;
		int x = -1;
		int y = -1;
		int sx = 0;
		int sy = 0;
		int sw = -1;
		int sh = -1;
		int r = 255;
		int g = 255;
		int b = 255;
		if (args.length >= 3)
		{
			texid = ConvertInteger.convert(args[0]);
			x = ConvertInteger.convert(args[1]);
			y = ConvertInteger.convert(args[2]);
		}
		else
		{
			throw new Exception("Not enough arguments!");
		}
		if (args.length >= 7)
		{
			sx = ConvertInteger.convert(args[3]);
			sy = ConvertInteger.convert(args[4]);
			sw = ConvertInteger.convert(args[5]);
			sh = ConvertInteger.convert(args[6]);
		}
		if (args.length >= 10)
		{
			r = ConvertInteger.convert(args[7]);
			g = ConvertInteger.convert(args[8]);
			b = ConvertInteger.convert(args[9]);
		}
		if (texid < 0 || texid >= gpu.textures.length)
		{
			throw new Exception("Texture ID "+texid+" doesn't exist");
		}
		if (gpu.textures[texid] == null)
		{
			throw new Exception("Texture ID "+texid+" doesn't exist");
		}
		if (sw < 0)
		{
			sw = gpu.textures[texid].getWidth();
		}
		if (sh < 0)
		{
			sh = gpu.textures[texid].getHeight();
		}
		if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255)
		{
			throw new Exception("Invalid RGB");
		}
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.drawTexture(gpu.textures[texid], x, y, sx, sy, sw, sh, r, g, b);
		}
	}
	
	@LuaMethod(name = "freeTexture", ret = Type.NULL, args = {Type.INT}, networked = true)
	public void freeTexture(GPU gpu, int tex) throws Exception
	{
		if (tex < 1 || tex >= gpu.textures.length)
		{
			throw new Exception("Texture ID "+tex+" doesn't exist");
		}
		if (gpu.textures[tex] == null)
		{
			throw new Exception("Texture ID "+tex+" doesn't exist");
		}
		if (gpu.bindedTexture == gpu.textures[tex])
		{
			gpu.bindedTexture = gpu.textures[0];
			gpu.bindedSlot = 0;
		}
		gpu.textures[tex] = null;
	}
	
	@LuaMethod(name = "line", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT}, networked = true)
	public void line(GPU gpu, int x1, int y1, int x2, int y2, int r, int g, int b)
	{
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.line(r, g, b, x1, y1, x2, y2);
		}
	}
	
	@LuaMethod(name = "getSize", ret = Type.OBJECTS)
	public Object[] getSize(GPU gpu)
	{
		if (gpu.bindedTexture == null) return new Object[]{-1,-1};
		return new Object[]{gpu.bindedTexture.getWidth(),gpu.bindedTexture.getHeight()};
	}
	
	@LuaMethod(name = "getTextureSize", ret = Type.OBJECTS)
	public Object[] getTextureSize(GPU gpu)
	{
		if (gpu.bindedTexture == null) return new Object[]{-1,-1};
		return new Object[]{gpu.bindedTexture.getWidth(),gpu.bindedTexture.getHeight()};
	}
	
	@LuaMethod(name = "setTransparent", ret = Type.NULL, args = {Type.INT,Type.BOOLEAN}, networked = true)
	public void setTransparent(GPU gpu, int texid, boolean transparent) throws Exception
	{
		if (texid < 0 || texid > gpu.textures.length)
		{
			throw new Exception("Texture "+texid+" doesn't exist");
		}
		if (gpu.textures[texid] == null)
			throw new Exception("Texture "+texid+" doesn't exist");
		gpu.textures[texid].setTransparent(transparent);
	}
	
	@LuaMethod(name = "setTransparencyColor", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT}, networked = true)
	public void setTransparencyColor(GPU gpu, int texid, int r, int g, int b) throws Exception
	{
		if (texid < 0 || texid > gpu.textures.length)
		{
			throw new Exception("Texture "+texid+" doesn't exist");
		}
		if (gpu.textures[texid] == null)
			throw new Exception("Texture "+texid+" doesn't exist");
		gpu.textures[texid].setTransparencyColor(r, g, b);
	}
	
	@LuaMethod(name = "getColorRGB", ret = Type.OBJECTS, args = {Type.INT,Type.INT})
	public Object[] getColorRGB(GPU gpu, int x, int y)
	{
		if (gpu.bindedTexture == null) return new Object[]{0,0,0};
		int[] col = gpu.bindedTexture.getRGB(x,y);
		return new Object[]{col[0],col[1],col[2]};
	}
	
	@LuaMethod(name = "getPixel", ret = Type.OBJECTS, args = {Type.INT,Type.INT})
	public Object[] getPixel(GPU gpu, int x, int y)
	{
		if (gpu.bindedTexture == null) return new Object[]{0,0,0};
		int[] col = gpu.bindedTexture.getRGB(x,y);
		return new Object[]{col[0],col[1],col[2]};
	}
	
	@LuaMethod(name = "rectangle", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT}, networked = true)
	public void rectangle(GPU gpu, int x, int y, int w, int h, int r, int g, int b)
	{
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.rect(r, g, b, x, y, w, h);
		}
	}
	
	@LuaMethod(name = "filledRectangle", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT}, networked = true)
	public void filledRectangle(GPU gpu, int x, int y, int w, int h, int r, int g, int b)
	{
		if (gpu.bindedTexture != null)
		{
			gpu.bindedTexture.filledRect(r, g, b, x, y, w, h);
		}
	}
	
	@LuaMethod(name = "setBPP", ret = Type.NULL, args = {Type.INT}, networked = true)
	public void setBPP(GPU gpu, int bpp)
	{
		gpu.changeBitDepth(bpp);
	}
	
	@LuaMethod(name = "getBindedTexture", ret = Type.INT)
	public int getBindedTexture(GPU gpu)
	{
		return gpu.bindedSlot;
	}
	
	@LuaMethod(name = "getBPP", ret = Type.INT)
	public int getBPP(GPU gpu)
	{
		return gpu.bpp;
	}
	
	@LuaMethod(name = "getPixelNative", ret = Type.INT, args = {Type.INT,Type.INT})
	public int getPixelNative(GPU gpu, int x, int y)
	{
		return 0; //TODO: Get this working
	}
	
	@LuaMethod(name = "setPixels", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.MAP}, networked = true)
	public void setPixels(GPU gpu, int x, int y, int w, int h, Map map) throws Exception
	{
		int size = w*h*3;
		//One of the things I hate is that ComputerCraft uses Doubles for all their values
		for (double i = 1; i<=size; i++)
		{
			if (!map.containsKey(i))
				throw new Exception("Not enough elements to fill area! Missing "+(i-(w*h*3)));
		}
		if (gpu.bindedTexture != null)
		{
			double i = 0;
			for (int cx = 0; cx<w; cx++)
			{
				for (int cy = 0; cy<h; cy++)
				{
					gpu.bindedTexture.plot(ConvertInteger.convert(map.get(i*3)), ConvertInteger.convert(map.get(i*3+1)), ConvertInteger.convert(map.get(i*3+2)), cx+x, cy+x);
					i++;
				}
			}
		}
	}
	
	@LuaMethod(name = "setPixelsYX", ret = Type.NULL, args = {Type.INT,Type.INT,Type.INT,Type.INT,Type.MAP}, networked = true)
	public void setPixelsYX(GPU gpu, int x, int y, int w, int h, Map map) throws Exception
	{
		int size = w*h*3;
		//One of the things I hate is that ComputerCraft uses Doubles for all their values
		for (double i = 1; i<=size; i++)
		{
			if (!map.containsKey(i))
				throw new Exception("Not enough elements to fill area! Missing "+(i-(w*h*3)));
		}
		if (gpu.bindedTexture != null)
		{
			double i = 0;
			for (int cx = 0; cx<h; cx++)
			{
				for (int cy = 0; cy<w; cy++)
				{
					gpu.bindedTexture.plot(ConvertInteger.convert(map.get(i*3)), ConvertInteger.convert(map.get(i*3+1)), ConvertInteger.convert(map.get(i*3+2)), cx+x, cy+x);
					i++;
				}
			}
		}
	}
}
