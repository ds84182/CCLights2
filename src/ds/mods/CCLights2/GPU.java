package ds.mods.CCLights2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;
import java.util.UUID;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.network.PacketHandler;
import ds.mods.CCLights2.network.PacketSplitter;

public class GPU {
	public Texture[] textures;
	public int maxmem;
	public Stack<DrawCMD> drawlist;
	public int drawlisthash;
	public Texture bindedTexture;
	public int bindedSlot;
	public ArrayList<Monitor> monitors = new ArrayList<Monitor>();
	public Monitor currentMonitor;
	public TileEntityGPU tile;
	public ArrayList<Packet> pendingPackets;
	public UUID uuid;

	public GPU(int gfxmem)
	{
		textures = new Texture[8192];
		drawlist = new Stack<DrawCMD>();
		pendingPackets = new ArrayList<Packet>();
		maxmem = gfxmem;
	}
	
	public Monitor getMonitor() {
		return currentMonitor;
	}
	
	public void addMonitor(Monitor mon)
	{
		monitors.add(mon);
		System.out.println("Added monitor "+mon.getWidth()+"x"+mon.getHeight()+" "+mon);
	}
	
	public void removeMonitor(Monitor mon)
	{
		monitors.remove(mon);
		System.out.println("Rem monitor "+mon.getWidth()+"x"+mon.getHeight()+" "+mon);
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
	}

	public void setMonitor(Monitor mon) {
		if (!monitors.contains(mon))
		{
			addMonitor(mon);
		}
		this.currentMonitor = mon;
		System.out.println("Monitor set!");
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
		if (getFreeMemory()-(w*h)<0)
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
	
	public void sendPacketToClient(Packet packet)
	{
		if (tile == null)
			throw new IllegalArgumentException("GPU cannot send packet without Tile Entity!");
		pendingPackets.add(packet);
	}
	public void sendPacketNow(Packet packet)
	{
		if (tile == null)
			throw new IllegalArgumentException("GPU cannot send packet without Tile Entity!");
		PacketSplitter.sendPacketToAllInDimension((Packet250CustomPayload) packet, tile.worldObj.provider.dimensionId);
	}
	
	public Object[] processCommand(DrawCMD cmd) throws Exception
	{
		if (bindedTexture == null)
		{
			return null;
		}
		switch(cmd.cmd)
		{
			case 0:
			{
				//Clear//
				bindedTexture.fill(cmd.args[0],cmd.args[1],cmd.args[2]);
				break;
			}
			case 1:
			{
				//Plot//
				bindedTexture.plot(cmd.args[0],cmd.args[1],cmd.args[2],cmd.args[3],cmd.args[4]);
				break;
			}
			case 2:
			{
				//drawTexture//
				if (cmd.args[0] == 0)
				{
					//Small version//
					bindedTexture.drawTexture(textures[cmd.args[1]], cmd.args[2],cmd.args[3]);
				}
				else if (cmd.args[0] == 1)
				{
					//Large version//
					bindedTexture.drawTexture(textures[cmd.args[1]], cmd.args[2],cmd.args[3], cmd.args[4],cmd.args[5], cmd.args[6],cmd.args[7],255,255,255);
				}
				else
				{
					bindedTexture.drawTexture(textures[cmd.args[1]], cmd.args[2],cmd.args[3], cmd.args[4],cmd.args[5], cmd.args[6],cmd.args[7],cmd.args[8], cmd.args[9],cmd.args[10]);
				}
				break;
			}
			case 3:
			{
				//line//
				bindedTexture.line(cmd.args[0],cmd.args[1],cmd.args[2],cmd.args[3],cmd.args[4],cmd.args[5],cmd.args[6]);
				break;
			}
			case 4:
			{
				throw new Exception("Transparency is depreciated.");
			}
			case 5:
			{
				throw new Exception("Transparency is depreciated.");
			}
			case 6:
			{
				//New Texture//
				return new Object[]{newTexture(cmd.args[0],cmd.args[1])};
			}
			case 7:
			{
				//Bind Texture//
				bindedTexture = textures[cmd.args[0]];
				bindedSlot = cmd.args[0];
				break;
			}
			case 8:
			{
				//Delete Texture//
				if (bindedTexture == textures[cmd.args[0]])
				{
					bindedTexture = textures[0];
					bindedSlot = 0;
				}
				textures[cmd.args[0]] = null;
				break;
			}
			case 9:
			{
				bindedTexture.rect(cmd.args[0],cmd.args[1],cmd.args[2],cmd.args[3],cmd.args[4],cmd.args[5],cmd.args[6]);
				break;
			}
			case 10:
			{
				bindedTexture.filledRect(cmd.args[0],cmd.args[1],cmd.args[2],cmd.args[3],cmd.args[4],cmd.args[5],cmd.args[6]);
				break;
			}
			case 11:
			{
				throw new Exception("Bit Depths are depricated.");
			}
			case 12:
			{
				int i = 5;
				int type = cmd.args[0];
				if (type == 0)
				{
					for (int x = 0; x<cmd.args[1]; x++)
					{
						for (int y = 0; y<cmd.args[2]; y++)
						{
							bindedTexture.plot(cmd.args[i++], cmd.args[i++], cmd.args[i++], x+cmd.args[3], y+cmd.args[4]);
						}
					}
				}
				else
				{
					for (int y = 0; y<cmd.args[2]; y++)
					{
						for (int x = 0; x<cmd.args[1]; x++)
						{
							bindedTexture.plot(cmd.args[i++], cmd.args[i++], cmd.args[i++], x+cmd.args[3], y+cmd.args[4]);
						}
					}
				}
				break;
			}
			case 13:
			{
				textures[cmd.args[0]].flipV();
				break;
			}
			case 14:
			{
				int id = newTexture(cmd.args[0],cmd.args[1]);
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
						tex.plot(cmd.args[i++], cmd.args[i++], cmd.args[i++], x, y);
					}
				}
				return new Object[]{id};
			}
			case 15:
			{
				String str = "";
				for (int i = 0; i<cmd.args.length-5; i++)
				{
					str = str+String.valueOf((char)cmd.args[5+i]);
				}
				bindedTexture.drawText(str, cmd.args[0], cmd.args[1], cmd.args[2], cmd.args[3], cmd.args[4]);
				break;
			}
		}
		return null;
	}
	
	public void processSendList()
	{
		if (!drawlist.isEmpty())
		{
			Stack<DrawCMD> copy = (Stack<DrawCMD>) drawlist.clone();
			if (!tile.worldObj.isRemote)
			{
				Packet250CustomPayload packet = new Packet250CustomPayload();
				packet.channel = "CCLights2";
				ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		    	DataOutputStream outputStream = new DataOutputStream(bos);
		    	try {
		    		outputStream.writeByte(PacketHandler.NET_GPUDRAWLIST);
					outputStream.writeInt(tile.xCoord);
					outputStream.writeInt(tile.yCoord);
					outputStream.writeInt(tile.zCoord);
					outputStream.writeInt(copy.size());
					for (int i = copy.size()-1; i>-1; i--)
					{
						outputStream.writeInt(copy.get(i).cmd);
						outputStream.writeInt(copy.get(i).args.length);
						for (int g = 0; g<copy.get(i).args.length; g++)
						{
							outputStream.writeInt(copy.get(i).args[g]);
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    	packet.data = bos.toByteArray();
		    	packet.length = bos.size();
		    	sendPacketNow(packet);
			}
			drawlist.clear();
		}
	}
}
