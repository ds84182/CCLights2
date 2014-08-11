package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Stack;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;

import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.network.PacketSenders;

public class GPU {
	/**
	 * Array of textures
	 */
	public Texture[] textures;
	
	/**
	 * Maximum ammout of "gpu memory" can be used
	 */
	public int maxmem;
	
	/**
	 * Draw list for server sending
	 */
	public Deque<DrawCMD> drawlist;
	
	/**
	 * The current binded texture
	 */
	public Texture bindedTexture;
	
	/**
	 * The slot that is binded
	 */
	public int bindedSlot;
	
	/**
	 * List of connected monitors
	 */
	public ArrayList<Monitor> monitors = new ArrayList<Monitor>();
	
	/**
	 * The current monitor
	 */
	public Monitor currentMonitor;
	
	/**
	 * Tile entity that contains the GPU
	 */
	public TileEntityGPU tile;
	
	/**
	 * The current draw color
	 */
	public Color color = Color.white;
	
	/**
	 * The transformation stack
	 */
	public Stack<AffineTransform> transformStack = new Stack<AffineTransform>();
	
	/**
	 * The current transform
	 */
	public AffineTransform transform = new AffineTransform();

	/**
	 * Default constructor for the GPU
	 * @param gfxmem The amount of "gpu memory" this GPU object has
	 */
	public GPU(int gfxmem)
	{
		textures = new Texture[8192];
		drawlist = new ArrayDeque<DrawCMD>();
		maxmem = gfxmem;
	}
	
	/**
	 * Returns the current monitor
	 * @return the current monitor
	 */
	public Monitor getMonitor() {
		return currentMonitor;
	}
	
	/**
	 * Adds a monitor to the monitor list and sets it as the current monitor.
	 * @param mon The monitor to add
	 */
	public void addMonitor(Monitor mon)
	{
		monitors.add(mon);
		currentMonitor = mon;
		textures[0] = mon.tex;
	}
	
	/**
	 * Removes a monitor from the monitor list, if it is the current monitor then it will select a new monitor
	 * @param mon
	 */
	public void removeMonitor(Monitor mon)
	{
		monitors.remove(mon);
		if (currentMonitor == mon)
		{
			textures[0] = null;
			currentMonitor = null;
			for (Monitor m : monitors)
			{
				currentMonitor = m; break;
			}
			if (currentMonitor != null)
			{
				textures[0] = currentMonitor.tex;
			}
		}
		mon.tex.fill(Color.black);
	}

	/**
	 * Sets the monitor to mon, if it is not in the monitor list then it is added
	 * @param mon The monitor to set
	 */
	public void setMonitor(Monitor mon) {
		if (!monitors.contains(mon))
		{
			addMonitor(mon);
		}
		currentMonitor = mon;
		if (bindedSlot == 0)
		{
			bindedTexture = mon.getTex();
		}
		textures[0] = mon.getTex();
	}
	
	/**
	 * Gets the ammout of used memory
	 * @return The ammount of used memory
	 */
	public int getUsedMemory()
	{
		int used = 0;
		for (int i=1; i<textures.length; i++)
		{
			if (textures[i]!=null)
			{
				used+=textures[i].getMemoryUse();
			}
		}
		return used;
	}
	
	/**
	 * Gets the ammount of free memory
	 * @return The amount of free memory
	 */
	public int getFreeMemory()
	{
		return maxmem-getUsedMemory();
	}
	
	/**
	 * Binds a texture, throws an exception if it doesn't exist
	 * @param texid The texture to bind
	 * @throws Exception
	 */
	public void bindTexture(int texid) throws Exception
	{
		if (texid < 0 && texid >= textures.length)
			throw new Exception("Texture id out of range");
		if (textures[texid] == null)
			throw new Exception("Texture doesn't exist!");
		bindedTexture = textures[texid];
		bindedSlot = texid;
	}
	
	/**
	 * Creates a new texture with a width and height
	 * @param w Width of the texture
	 * @param h Height of the texture
	 * @return Returns -1 if the free memory is too low for the texture
	 * Returns -2 if all texture slots are exausted
	 * Else, returns the texture id of the new texture
	 */
	public int newTexture(int w, int h)
	{
		if (getFreeMemory() <= 0)
		{
			return -1;
		}
		else
		{
			for (int i=1; i<textures.length; i++)
			{
				if (textures[i]==null)
				{
					textures[i] = new Texture(w, h);
					return i;
				}
			}
			return -2;
		}
	}
	
	/**
	 * Pushes the transform on the transformStack
	 */
	public void push()
	{
		transformStack.push(transform);
		transform = (AffineTransform) transform.clone();
	}
	
	/**
	 * Pops the last transform off the stack
	 */
	public void pop()
	{
		transform = transformStack.pop();
	}
	
	/**
	 * Translates the transform by x and y
	 * @param x The x coordinate to translate to
	 * @param y The y coordinate to translate to
	 */
	public void translate(double x, double y)
	{
		transform.translate(x, y);
	}
	
	/**
	 * Rotates the transform by r
	 * @param r The radians to rotate the transform by
	 */
	public void rotate(double r)
	{
		transform.rotate(r);
	}
	
	/**
	 * Rotates the transform by r about x and y
	 * @param r
	 * @param x
	 * @param y
	 */
	public void rotate(double r, double x, double y)
	{
		transform.rotate(r,x,y);
	}
	
	/**
	 * Scales the transform by s in both directions
	 * @param s
	 */
	public void scale(double s)
	{
		transform.scale(s, s);
	}
	
	/**
	 * Scales the transform by sx and sy
	 * @param sx
	 * @param sy
	 */
	public void scale(double sx, double sy)
	{
		transform.scale(sx, sy);
	}
	
	/**
	 * Process the DrawCMD, throws an exception if the command fails
	 * @param cmd The command to process
	 * @return Return values for ComputerCraft
	 * @throws Exception
	 */
	public Object[] processCommand(DrawCMD cmd) throws Exception
	{
		if (cmd == null)
			return null;
		if (bindedTexture == null)
		{
			return null;
		}
			bindedTexture.transform = transform;
			switch(cmd.cmd)
			{
				case SetColor:
				{
					color = cmd.args.length == 3 ? new Color((Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2]) : new Color((Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case Fill:
				{
					//Clear//
					bindedTexture.fill(color);
					break;
				}
				case Plot:
				{
					//Plot//
					bindedTexture.plot(color,(Integer) cmd.args[0],(Integer) cmd.args[1]);
					break;
				}
				case DrawTexture:
				{
					//drawTexture//
					if ((Integer)cmd.args[0] == 0)
					{
						//Small version//
						bindedTexture.drawTexture(textures[(Integer) cmd.args[1]], (Integer) cmd.args[2],(Integer) cmd.args[3], color);
					}
					else
					{
						bindedTexture.drawTexture(textures[(Integer) cmd.args[1]], (Integer) cmd.args[2],(Integer) cmd.args[3], (Integer) cmd.args[4],(Integer) cmd.args[5], (Integer) cmd.args[6],(Integer) cmd.args[7],color);
					}
					break;
				}
				case Line:
				{
					//line//
					bindedTexture.line(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case CreateTexture:
				{
					//New Texture//
					return new Object[]{newTexture((Integer) cmd.args[0],(Integer) cmd.args[1])};
				}
				case BindTexture:
				{
					//Bind Texture//
					bindedTexture = textures[(Integer) cmd.args[0]];
					bindedSlot = (Integer) cmd.args[0];
					break;
				}
				case FreeTexture:
				{
					//Delete Texture//
					if (bindedTexture == textures[(Integer) cmd.args[0]])
					{
						bindedTexture = textures[0];
						bindedSlot = 0;
					}
					textures[(Integer) cmd.args[0]] = null;
					break;
				}
				case Rectangle:
				{
					bindedTexture.rect(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case FilledRectangle:
				{
					bindedTexture.filledRect(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case SetPixels:
				{
					int i = 4;
					for (int x = 0; x<(Integer)cmd.args[1]; x++)
					{
					 for (int y = 0; y<(Integer)cmd.args[2]; y++)
					 {
					   bindedTexture.plot(new Color((Integer) cmd.args[i++], (Integer) cmd.args[i++], (Integer) cmd.args[i++], (Integer) cmd.args[i++]), x+(Integer)cmd.args[3], y+(Integer)cmd.args[4]);
					 }
					}
					break;
				}
				case FlipVertically:
				{
					textures[(Integer) cmd.args[0]].flipV();
					break;
				}
				case Import:
				{
					if (cmd.args[0] instanceof Object[])
					{
						Object[] old = (Object[]) cmd.args[0];
						cmd.args[0] = new Byte[old.length];
						Byte[] n = (Byte[]) cmd.args[0];
						for (int i=0; i<old.length; i++)
						{
							n[i] = (Byte) old[i];
						}
					}
					BufferedImage img = loadImage(ArrayUtils.toPrimitive((Byte[])cmd.args[0]));
					//image loaded successfully time to create texture
					int id = newTexture(img.getWidth(),img.getHeight());
					if (id == -1) {
						throw new Exception("Not enough memory for texture");
					} else if (id == -2) {
						throw new Exception("Not enough texture slots");
					} else {
					Texture tex = textures[id];
					tex.graphics.drawImage(img, 0, 0, null);
					return new Object[]{id};
					}
				}
				case DrawText:
				{
					String str = "";
					for (int i = 0; i<cmd.args.length-2; i++)
					{
						str = str+String.valueOf(cmd.args[2+i]);
					}
					bindedTexture.drawText(str, (Integer) cmd.args[0], (Integer) cmd.args[1], color);
					break;
				}
				case Transelate:
				{
					translate((Double)cmd.args[0],(Double)cmd.args[1]);
					break;
				}
				case Rotate:
				{
					rotate((Double)cmd.args[0]);
					break;
				}
				case RotateAround:
				{
					rotate((Double)cmd.args[0],(Double)cmd.args[1],(Double)cmd.args[2]);
					break;
				}
				case Scale:
				{
					scale((Double)cmd.args[0],(Double)cmd.args[1]);
					break;
				}
				case Push:
				{
					push();
					break;
				}
				case Pop:
				{
					pop();
					break;
				}
				case Blur:
				{
					textures[(Integer) cmd.args[0]].blur();
					break;
				}
				case ClearRectangle:
				{
					bindedTexture.clearRect(color,(Integer) cmd.args[0],(Integer) cmd.args[1],(Integer) cmd.args[2],(Integer) cmd.args[3]);
					break;
				}
				case Origin:
				{
					transform = new AffineTransform();
					break;
				}
			default:
				break;
			}
		return null;
	}
	
	/**
	 * Load an image from a bytearray
	 */
	public BufferedImage loadImage(byte[] data) throws Exception {
		try {
			return ImageIO.read(new ByteArrayInputStream(data));
		} catch (IOException e) {
			throw new Exception("Failed to Load Image provided, invalid data?");
		}
	}
	
	/**
	 * Send the drawlist to the server and clear it
	 */
	public void processSendList()
	{
		if (!drawlist.isEmpty())
		{
			if (!tile.worldObj.isRemote)
			{
		    	PacketSenders.sendPacketsNow(drawlist,tile);
			}
			drawlist.clear();
		}
	}
}
