package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Stack;
import java.util.UUID;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.network.PacketSenders;

public class GPU {
	public Texture[] textures;
	public int maxmem;
	public Deque<DrawCMD> drawlist;
	public int drawlisthash;
	public Texture bindedTexture;
	public int bindedSlot;
	public ArrayList<Monitor> monitors = new ArrayList<Monitor>();
	public Monitor currentMonitor;
	public TileEntityGPU tile;
	public UUID uuid;
	public Color color = Color.white;
	public Stack<AffineTransform> transformStack = new Stack<AffineTransform>();
	public AffineTransform transform = new AffineTransform();

	public GPU(int gfxmem)
	{
		textures = new Texture[8192];
		drawlist = new ArrayDeque<DrawCMD>();
		maxmem = gfxmem;
	}
	
	public Monitor getMonitor() {
		return currentMonitor;
	}
	
	public void addMonitor(Monitor mon)
	{
		monitors.add(mon);
		currentMonitor = mon;
		CCLights2.debug("Added monitor "+mon.getWidth()+"x"+mon.getHeight()+" "+mon);
	}
	
	public void removeMonitor(Monitor mon)
	{
		monitors.remove(mon);
		CCLights2.debug("Rem monitor "+mon.getWidth()+"x"+mon.getHeight()+" "+mon);
		if (currentMonitor == mon)
		{
			textures[0] = null;
			bindedTexture = null;
			currentMonitor = null;
			for (Monitor m : monitors)
			{
				currentMonitor = m; break;
			}
		}
		mon.tex.fill(Color.black);
	}

	public void setMonitor(Monitor mon) {
		if (!monitors.contains(mon))
		{
			addMonitor(mon);
		}
		this.currentMonitor = mon;
		CCLights2.debug("Monitor set!");
		bindedTexture = mon.getTex();
		textures[0] = bindedTexture;
		bindedSlot = 0;
	}
	
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
	
	public int getFreeMemory()
	{
		return maxmem-getUsedMemory();
	}
	
	public void bindTexture(int texid) throws Exception
	{
		if (textures[texid] == null)
			throw new Exception("Texture doesn't exist!");
		bindedTexture = textures[texid];
		bindedSlot = texid;
	}
	
	public int newTexture(int w, int h)
	{
		if (getFreeMemory()<0)
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
	
	public void push()
	{
		transformStack.push(transform);
		transform = (AffineTransform) transform.clone();
	}
	
	public void pop()
	{
		//System.out.println("pop");
		transform = transformStack.pop();
	}
	
	public void translate(double x, double y)
	{
		//System.out.println("translate "+x+", "+y);
		transform.translate(x, y);
	}
	
	public void rotate(double r)
	{
		transform.rotate(r);
	}
	
	public void rotate(double r, double x, double y)
	{
		transform.rotate(r,x,y);
	}
	
	public void scale(double s)
	{
		transform.scale(s, s);
	}
	
	public void scale(double sx, double sy)
	{
		transform.scale(sx, sy);
	}
	
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
				case -1:
				{
					color = cmd.args.length == 3 ? new Color((int) cmd.args[0],(int) cmd.args[1],(int) cmd.args[2]) : new Color((int) cmd.args[0],(int) cmd.args[1],(int) cmd.args[2],(int) cmd.args[3]);
					break;
				}
				case 0:
				{
					//Clear//
					bindedTexture.fill(color);
					break;
				}
				case 1:
				{
					//Plot//
					bindedTexture.plot(color,(int) cmd.args[0],(int) cmd.args[1]);
					break;
				}
				case 2:
				{
					//drawTexture//
					if (cmd.args[0] == 0)
					{
						//Small version//
						bindedTexture.drawTexture(textures[(int) cmd.args[1]], (int) cmd.args[2],(int) cmd.args[3], color);
					}
					else
					{
						bindedTexture.drawTexture(textures[(int) cmd.args[1]], (int) cmd.args[2],(int) cmd.args[3], (int) cmd.args[4],(int) cmd.args[5], (int) cmd.args[6],(int) cmd.args[7],color);
					}
					break;
				}
				case 3:
				{
					//line//
					bindedTexture.line(color,(int) cmd.args[0],(int) cmd.args[1],(int) cmd.args[2],(int) cmd.args[3]);
					break;
				}
				case 6:
				{
					//New Texture//
					return new Object[]{newTexture((int) cmd.args[0],(int) cmd.args[1])};
				}
				case 7:
				{
					//Bind Texture//
					bindedTexture = textures[(int) cmd.args[0]];
					bindedSlot = (int) cmd.args[0];
					//System.out.println("Binded texture "+cmd.args[0]);
					break;
				}
				case 8:
				{
					//Delete Texture//
					if (bindedTexture == textures[(int) cmd.args[0]])
					{
						bindedTexture = textures[0];
						bindedSlot = 0;
					}
					textures[(int) cmd.args[0]] = null;
					break;
				}
				case 9:
				{
					bindedTexture.rect(color,(int) cmd.args[0],(int) cmd.args[1],(int) cmd.args[2],(int) cmd.args[3]);
					break;
				}
				case 10:
				{
					bindedTexture.filledRect(color,(int) cmd.args[0],(int) cmd.args[1],(int) cmd.args[2],(int) cmd.args[3]);
					break;
				}
				case 12:
				{
					int i = 5;
					int type = (int) cmd.args[0];
					if (type == 0)
					{
						for (int x = 0; x<cmd.args[1]; x++)
						{
							for (int y = 0; y<cmd.args[2]; y++)
							{
								bindedTexture.plot(new Color((int) cmd.args[i++], (int) cmd.args[i++], (int) cmd.args[i++]), (int) (x+cmd.args[3]), (int) (y+cmd.args[4]));
							}
						}
					}
					else
					{
						for (int y = 0; y<cmd.args[2]; y++)
						{
							for (int x = 0; x<cmd.args[1]; x++)
							{
								bindedTexture.plot(new Color((int) cmd.args[i++], (int) cmd.args[i++], (int) cmd.args[i++]), (int) (x+cmd.args[3]), (int) (y+cmd.args[4]));
							}
						}
					}
					break;
				}
				case 13:
				{
					textures[(int) cmd.args[0]].flipV();
					break;
				}
				case 14:
				{
					int id = newTexture((int) cmd.args[0],(int) cmd.args[1]);
					if (id == -1) {
						throw new Exception("Not enough memory for texture");
					} else if (id == -2) {
						throw new Exception("Not enough texture slots");
					}
					Texture tex = textures[id];
					int i = 2;
					for (int x = 0; x<cmd.args[0]; x++)
					{
						for (int y = 0; y<cmd.args[1]; y++)
						{
							int col = (int) cmd.args[i++];
							tex.plot(new Color(col,true), x, y);
						}
					}
					return new Object[]{id};
				}
				case 15:
				{
					String str = "";
					for (int i = 0; i<cmd.args.length-2; i++)
					{
						str = str+String.valueOf((char)cmd.args[2+i]);
					}
					bindedTexture.drawText(str, (int) cmd.args[0], (int) cmd.args[1], color);
					break;
				}
				case 16:
				{
					translate(cmd.args[0],cmd.args[1]);
					break;
				}
				case 17:
				{
					rotate(cmd.args[0]);
					break;
				}
				case 18:
				{
					rotate(cmd.args[0],cmd.args[1],cmd.args[2]);
					break;
				}
				case 19:
				{
					scale(cmd.args[0],cmd.args[1]);
					break;
				}
				case 20:
				{
					push();
					break;
				}
				case 21:
				{
					pop();
					break;
				}
				case 22:
				{
					textures[(int) cmd.args[0]].blur();
					break;
				}
				case 23:
				{
					bindedTexture.clearRect(color,(int) cmd.args[0],(int) cmd.args[1],(int) cmd.args[2],(int) cmd.args[3]);
					break;
				}
			}
		return null;
	}
	
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
