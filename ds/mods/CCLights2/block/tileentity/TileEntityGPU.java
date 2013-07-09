package ds.mods.CCLights2.block.tileentity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;
import java.util.TreeMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Convert;
import ds.mods.CCLights2.DrawCMD;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.Monitor;
import ds.mods.CCLights2.debug.DebugWindow;

public class TileEntityGPU extends TileEntity implements IPeripheral {
	public GPU gpu;
	public Monitor mon;
	public ForgeDirection mondir;
	public boolean wait = false;
	public boolean wait2 = false;
	public int ticks;
	public boolean sendDLREQ = false;
	public ArrayList<DrawCMD> newarr = new ArrayList<DrawCMD>();
	public IComputerAccess[] comp = new IComputerAccess[6];
	public boolean isGoing = true;
	public int emptyFor = 0;
	public TreeMap<String, Integer> playerToClickMap = new TreeMap<String, Integer>();
	public TreeMap<Integer, int[]> clickToDataMap = new TreeMap<Integer, int[]>();
	public Random rand = new Random();
	public int[] addedType = new int[1025];
	public DebugWindow wind;
	
	public TileEntityGPU()
	{
		gpu = new GPU(1024*8);
		gpu.tile = this;
		if (CCLights2.proxy.getClientWorld() != null)
		{
			//download textures//
			System.out.println("Client side GPU!");
			sendDLREQ = true;
		}
		else
		{
			System.out.println("Server side GPU!");
		}
	}
	
	public void startClick(Player player, int button, int x, int y)
	{
		int id = rand.nextInt();
		while (playerToClickMap.containsValue(id))
		{
			id = rand.nextInt();
		}
		playerToClickMap.put(((EntityPlayer) player).username, id);
		clickToDataMap.put(id, new int[]{button,x,y});
		
		String event = "monitor_down";
		Object[] args = new Object[]{button,x,y,id};
		for (int i = 0; i<6; i++)
		{
			if (comp[i] != null)
			{
				comp[i].queueEvent(event, args);
			}
		}
	}
	
	public void moveClick(Player player, int nx, int ny)
	{
		int id = playerToClickMap.get(((EntityPlayer) player).username);
		int[] data = clickToDataMap.get(id);
		int button = data[0];
		data[1] = nx;
		data[2] = ny;
		
		String event = "monitor_move";
		Object[] args = new Object[]{button,nx,ny,id};
		for (int i = 0; i<6; i++)
		{
			if (comp[i] != null)
			{
				comp[i].queueEvent(event, args);
			}
		}
	}
	
	public void endClick(Player player)
	{
		int id = playerToClickMap.get(((EntityPlayer) player).username);
		int[] data = clickToDataMap.get(id);
		int button = data[0];
		int x = data[1];
		int y = data[2];
		
		String event = "monitor_up";
		Object[] args = new Object[]{button,x,y,id};
		for (int i = 0; i<6; i++)
		{
			if (comp[i] != null)
			{
				comp[i].queueEvent(event, args);
			}
		}
		playerToClickMap.remove(((EntityPlayer) player).username);
		clickToDataMap.remove(id);
	}
	public boolean isMonitorConnected()
	{
		if (mondir == null)
			return false;
		TileEntity ftile = worldObj.getBlockTileEntity(xCoord+mondir.offsetX, yCoord+mondir.offsetY, zCoord+mondir.offsetZ);
		if (ftile != null)
		{
			if (ftile instanceof MonitorBase)
			{
				MonitorBase tile = (MonitorBase) worldObj.getBlockTileEntity(xCoord+mondir.offsetX, yCoord+mondir.offsetY, zCoord+mondir.offsetZ);
                return tile != null && tile.isGPUConnected() & tile.gpudir == mondir.getOpposite();
            }
		}
		return false;
	}
	
	public void connectToMonitor()
	{
		if (isMonitorConnected())
		{
			 if (Config.DEBUGS){
			System.out.println("Connected already.");}
			return;
		}
		for (int i=0; i<ForgeDirection.VALID_DIRECTIONS.length; i++)
		{
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
			TileEntity ftile = worldObj.getBlockTileEntity(xCoord+dir.offsetX, yCoord+dir.offsetY, zCoord+dir.offsetZ);
			if (ftile != null)
			{
				if (ftile instanceof MonitorBase)
				{
					MonitorBase tile = (MonitorBase) worldObj.getBlockTileEntity(xCoord+dir.offsetX, yCoord+dir.offsetY, zCoord+dir.offsetZ);
					if (tile != null)
					{
						System.out.println("Connecting!");
						tile.connect(gpu, dir, this);
						mon = tile.mon;
						mondir = dir;
						mon.tex.fill(255, 0, 0);
						gpu.setMon(mon);
						return;
					}
				}
				else
				{
					 if (Config.DEBUGS){
					System.out.println(dir.name());
					System.out.println(ftile.toString());}
				}
			}
		}
	}

	@Override
	public String getType() {
		return "GPU";
	}

	@Override
	public String[] getMethodNames() {
		return new String[] {"fill","createTexture","getFreeMemory","getTotalMemory","getUsedMemory","bindTexture","setColorRGB","plot","drawTexture","freeTexture","line","getSize","getTextureSize","setTransparent","setTransparencyColor","getColorRGB","getPixel","rectangle","filledRectangle","setBPP","getBindedTexture","getBPP","getNativePixel","setPixels","setPixelsYX","flipTextureV"};
	}

	@Override
	public synchronized Object[] callMethod(IComputerAccess computer, int method,
			Object[] args) throws Exception {
		if (wait)
			this.wait();
		switch (method)
		{
			case 0:
			{
				//Fill//
				if (args.length == 3)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2])};
					cmd.cmd = 0;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 1:
			{
				//Create Texture//
				if (args.length == 2)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1])};
					cmd.cmd = 6;
					cmd.args = nargs;
					Object[] ret = gpu.processCommand(cmd);
					newarr.add(cmd);
					int id = (Integer) ret[0];
					wait2 = false;
					if (id == -1)
					{
						throw new Exception("Not enough memory for texture");
					}
					else if (id == -2)
					{
						throw new Exception("Not enough texture slots");
					}
					else
					{
						return ret;
					}
				}
				break;
			}
			case 2:
			{
				return new Object[] {gpu.getFreeMemory()};
			}
			case 3:
			{
				return new Object[] {gpu.maxmem};
			}
			case 4:
			{
				return new Object[] {gpu.getUsedMemory()};
			}
			case 5:
			{
				if (args.length == 1)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0])};
					cmd.cmd = 7;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 6:case 7:
			{
				if (args.length == 5)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3]),Convert.toInt(args[4])};
					cmd.cmd = 1;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 8:
			{
				if (args.length == 3)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{0,Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2])};
					cmd.cmd = 2;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				else if (args.length == 7)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{1,Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3]),Convert.toInt(args[4]),Convert.toInt(args[5]),Convert.toInt(args[6])};
					cmd.cmd = 2;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				else if (args.length == 10)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{2,Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3]),Convert.toInt(args[4]),Convert.toInt(args[5]),Convert.toInt(args[6]),Convert.toInt(args[7]),Convert.toInt(args[8]),Convert.toInt(args[9])};
					cmd.cmd = 2;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 9:
			{
				if (args.length == 1)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0])};
					cmd.cmd = 8;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 10:
			{
				if (args.length == 7)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3]),Convert.toInt(args[4]),Convert.toInt(args[5]),Convert.toInt(args[6])};
					cmd.cmd = 3;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 11: case 12:
			{
				return new Object[]{gpu.bindedTexture.getWidth(),gpu.bindedTexture.getHeight()};
			}
			case 13:
			{
				if (args.length == 2)
				{
					boolean is = (Boolean) args[1];
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]), is ? 1 : 0};
					cmd.cmd = 4;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 14:
			{
				if (args.length == 4)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3])};
					cmd.cmd = 5;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 15: case 16:
			{
				if (args.length == 2)
				{
					int i = (Convert.toInt(args[1])*gpu.bindedTexture.getWidth())+Convert.toInt(args[0]);
					int r = gpu.bindedTexture.bytedata[i*3];
					int g = gpu.bindedTexture.bytedata[i*3+1];
					int b = gpu.bindedTexture.bytedata[i*3+2];
					return new Object[]{r&0xFF,g&0xFF,b&0xFF};
				}
				break;
			}
			case 17:
			{
				if (args.length == 7)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3]),Convert.toInt(args[4]),Convert.toInt(args[5]),Convert.toInt(args[6])};
					cmd.cmd = 9;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 18:
			{
				if (args.length == 7)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0]),Convert.toInt(args[1]),Convert.toInt(args[2]),Convert.toInt(args[3]),Convert.toInt(args[4]),Convert.toInt(args[5]),Convert.toInt(args[6])};
					cmd.cmd = 10;
					cmd.args = nargs;
					gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
				}
				break;
			}
			case 19:
			{
				if (args.length == 1)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0])};
					cmd.cmd = 11;
					cmd.args = nargs;
					Object[] ret = gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
					return ret;
				}
			}
			case 20:
			{
				return new Object[]{gpu.bindedSlot};
			}
			case 21:
			{
				return new Object[]{gpu.bpp};
			}
			case 22:
			{
				int i = (Convert.toInt(args[1])*gpu.bindedTexture.getWidth())+Convert.toInt(args[0]);
				int bit = gpu.bindedTexture.texture[i];
				Object[] ret = new Object[gpu.bpp];
				for (int at = 0; at<ret.length; at++)
				{
					ret[at] = (bit>>(at*8))&255;
				}
				return ret;
			}
			case 23:
			{
				if (args.length < 4)
				{
					throw new Exception("w, h, x, y, [r,g,b]... expected");
				}
				else
				{
					int w = Convert.toInt(args[0]);
					int h = Convert.toInt(args[1]);
					if (args.length<(w*h*3)+4)
					{
						throw new Exception("not enough arguments to fill area!");
					}
					else
					{
						//We send the arguments straight to the GPU!
						wait2 = true;
						DrawCMD cmd = new DrawCMD();
						int[] nargs = new int[(w*h*3)+4+1];
						nargs[0] = 0;
						for (int i = 1; i<(w*h*3)+4+1; i++)
						{
							nargs[i] = Convert.toInt(args[i-1]);
						}
						cmd.cmd = 12;
						cmd.args = nargs;
						Object[] ret = gpu.processCommand(cmd);
						newarr.add(cmd);
						wait2 = false;
						return ret;
					}
				}
			}
			case 24:
			{
				if (args.length < 4)
				{
					throw new Exception("w, h, x, y, [r,g,b]... expected");
				}
				else
				{
					int w = Convert.toInt(args[0]);
					int h = Convert.toInt(args[1]);
					if (args.length<(w*h*3)+4)
					{
						throw new Exception("not enough arguments to fill area!");
					}
					else
					{
						//We send the arguments straight to the GPU!
						wait2 = true;
						DrawCMD cmd = new DrawCMD();
						int[] nargs = new int[(w*h*3)+4+1];
						nargs[0] = 1;
						for (int i = 1; i<(w*h*3)+4+1; i++)
						{
							nargs[i] = Convert.toInt(args[i-1]);
						}
						cmd.cmd = 12;
						cmd.args = nargs;
						Object[] ret = gpu.processCommand(cmd);
						newarr.add(cmd);
						wait2 = false;
						return ret;
					}
				}
			}
			case 25:
			{
				if (args.length == 1)
				{
					wait2 = true;
					DrawCMD cmd = new DrawCMD();
					int[] nargs = new int[]{Convert.toInt(args[0])};
					cmd.cmd = 13;
					cmd.args = nargs;
					Object[] ret = gpu.processCommand(cmd);
					newarr.add(cmd);
					wait2 = false;
					return ret;
				}
			}
		}
		return null;
	}

	@Override
	public boolean canAttachToSide(int side) {
		return true;
	}

	@Override
	public void attach(IComputerAccess computer) {
		for (int i = 0; i<6; i++)
		{
			if (comp[i] == null)
			{
				comp[i] = computer;
				return;
			}
		}
	}

	@Override
	public void detach(IComputerAccess computer) {
		for (int i = 0; i<6; i++)
		{
			if (comp[i] == computer)
			{
				comp[i] = null;
				return;
			}
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setIntArray("addedTypes", addedType);
		nbt.setInteger("vram", gpu.maxmem);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		addedType = nbt.getIntArray("addedTypes");
		if (addedType == null)
		{
			addedType = new int[1025];
		}
		else if (addedType.length != 1025)
		{
			addedType = new int[1025];
		}
		int init = gpu.maxmem;
		gpu.maxmem = nbt.getInteger("vram");
		if (init>gpu.maxmem)
		{
			gpu.maxmem = init;
		}
	}
	
	@Override
	public synchronized void updateEntity()
	{
        if (wind != null)
        {
            wind.update();
        }
		connectToMonitor();
		//if (!wait2)
		{
			wait = true;
			if (gpu.drawlist.isEmpty())
			{
				ArrayList<DrawCMD> arr = (ArrayList<DrawCMD>) newarr.clone();
				newarr.clear();
				gpu.drawlist = new Stack<DrawCMD>();
				for (int i = arr.size()-1; i>-1; i--)
				{
					isGoing = true;
					gpu.drawlist.push(arr.get(i));
				}
			}
			if (gpu.drawlist.isEmpty())
			{
				if (isGoing)
				{
					emptyFor++;
					if (emptyFor > 40)
					{
						emptyFor = 0;
						isGoing = false;
					}
					else
					{
						isGoing = true;
					}
				}
			}
			else
			{
				isGoing = true;
			}
			gpu.processSendList();
			gpu.pendingPackets.clear();
			wait = false;
			synchronized(this)
			{
				this.notifyAll();
			}
		}
		if (sendDLREQ & ticks++ % 20 == 0)
		{
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = "GPUDownload";
			ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
	    	DataOutputStream outputStream = new DataOutputStream(bos);
	    	try {
				outputStream.writeInt(xCoord);
				outputStream.writeInt(yCoord);
				outputStream.writeInt(zCoord);
				outputStream.writeInt(worldObj.provider.dimensionId);
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	packet.data = bos.toByteArray();
	    	packet.length = bos.size();
	    	System.out.println("Sent DL Request to server!");
	    	PacketDispatcher.sendPacketToServer(packet);
	    	sendDLREQ = false;
		}
	}
}
