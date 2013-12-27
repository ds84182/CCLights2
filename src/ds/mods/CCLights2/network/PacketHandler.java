package ds.mods.CCLights2.network;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import dan200.computer.api.IComputerAccess;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.ClientDrawThread;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityaAdvancedlight;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Texture;

public class PacketHandler implements IPacketHandler {
	//"GPUDrawlist", "GPUEvent", "GPUDownload", "GPUMouse", "GPUKey", "GPUTile","LIGHT"
	public static final byte NET_SPLITPACKET = -1;
	public static final byte NET_GPUDRAWLIST = 0;
	public static final byte NET_GPUEVENT = 1;
	public static final byte NET_GPUDOWNLOAD = 2;
	public static final byte NET_GPUMOUSE = 3;
	public static final byte NET_GPUKEY = 4;
	public static final byte NET_GPUTILE = 5;
	public static final byte NET_GPUINIT = 6;
	public static final byte NET_LIGHT = 7;
	
	public HashMap<Short,SplitPacket> splitPackets = new HashMap<Short,SplitPacket>();
	
	public boolean doThreadding = true;
	public ClientDrawThread thread;
	{
		if (doThreadding)
		{
			thread = new ClientDrawThread();
			CCLights2.debug("Start thread");
			thread.start();
		}
	}
	
	@Override
	public void onPacketData(INetworkManager manager,
			Packet250CustomPayload packet, Player player) {
		ByteArrayDataInput dat = ByteStreams.newDataInput(packet.data);
		byte typ = dat.readByte();
		switch (typ)
		{
			case NET_SPLITPACKET:
			{
				//Split packet.
				short id = dat.readShort();
				if (splitPackets.get(id) != null)
				{
					splitPackets.get(id).addPacket(packet.data);
					if (splitPackets.get(id).finish)
					{
						CCLights2.debug("Split packet finished.");
						splitPackets.put(id, null);
					}
				}
				else
				{
					SplitPacket p = new SplitPacket(packet.data, this, manager, player, packet.channel);
					if (!p.finish) splitPackets.put(id, p);
				}
				break;
			}
			case (NET_GPUDRAWLIST):
			{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				int len = dat.readInt();
				TileEntityGPU tile = (TileEntityGPU) CCLights2.proxy.getClientWorld().getBlockTileEntity(x, y, z);
				if (tile != null)
				{
					GPU gpu = tile.gpu;
					//TODO> alekso56: I might put clientside drawing in another thread so that Minecraft doesn't get stalled when a graphically intensive packet comes
					//System.out.println(len+" drawcmds");
					int[] most = new int[30];
					for (int i = 0; i<len; i++)
					{
						DrawCMD cmd = new DrawCMD();
						cmd.cmd = dat.readInt();
						most[cmd.cmd+1]++;
						int lent = dat.readInt();
						cmd.args = new double[lent];
						for (int g = 0; g<lent; g++)
						{
							cmd.args[g] = dat.readDouble();
						}
						if (!doThreadding)
							try {
								tile.gpu.processCommand(cmd);
							} catch (Exception e) {
								//MEH.
								e.printStackTrace();
							}
						else
						{
							if (!thread.isAlive())
							{
								CCLights2.debug("The client draw thread died, restarting");
								thread = new ClientDrawThread();
								thread.start();
							}
							if (thread.draws.get(tile.gpu) == null)
							{
								thread.draws.put(tile.gpu, new ArrayDeque<DrawCMD>());
							}
							thread.draws.get(tile.gpu).addLast(cmd);
						}
					}
					int n = -1;
					int ind = 0;
					for (int i=0; i<most.length; i++)
					{
						if (n<most[i])
						{
							n=most[i];
							ind=i;
						}
					}
					//System.out.println("Most used drawcmd: "+(ind-1)+" with "+n+" uses");
				}
				break;
			}
			case (NET_GPUEVENT):
			{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				int dim = dat.readInt();
				World world = null;
				for (int i = 0; i<MinecraftServer.getServer().worldServers.length; i++)
				{
					if (MinecraftServer.getServer().worldServers[i] != null)
					{
						if (MinecraftServer.getServer().worldServers[i].provider.dimensionId == dim)
						{
							world = MinecraftServer.getServer().worldServers[i];
						}
					}
				}
				TileEntityMonitor mtile = (TileEntityMonitor) world.getBlockTileEntity(x, y, z);
				String event = dat.readUTF();
				int len = dat.readInt();
				Object[] args = new Object[len];
				for (int i = 0; i<len; i++)
				{
					int type = dat.readInt();
					switch (type)
					{
						case 0:
						{
							args[i] = dat.readInt();
							break;
						}
						case 1:
						{
							args[i] = dat.readUTF();
							break;
						}
						case 2:
						{
							args[i] = String.valueOf(dat.readChar());
							break;
						}
					}
				}
				for (GPU g : mtile.mon.gpu)
				{
					TileEntityGPU tile = g.tile;
					if (tile != null)
					{
						for (IComputerAccess c : tile.comp)
							if (c != null)
							{
								c.queueEvent(event, args);
							}
					}
				}
				break;
			}
			case (NET_GPUINIT):
			{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				TileEntityGPU tile = (TileEntityGPU) CCLights2.proxy.getClientWorld().getBlockTileEntity(x, y, z);
				if (tile == null) return;
				if (tile.gpu == null) return;
				tile.gpu.color = new Color(dat.readInt(),true);
				double[] matrix = new double[6];
				readMatrix(dat,matrix);
				tile.gpu.transform = new AffineTransform(matrix);
				tile.gpu.transformStack.clear();
				for (int i=0; i<dat.readInt(); i++)
				{
					readMatrix(dat,matrix);
					tile.gpu.transformStack.push(new AffineTransform(matrix));
				}
				break;
			}
			case (NET_GPUDOWNLOAD):
			{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				if (player instanceof EntityPlayerMP)
				{
					//Server//
					CCLights2.debug("Got DL packet from client!");
					int dim = dat.readInt();
					World world = null;
					for (int i = 0; i<MinecraftServer.getServer().worldServers.length; i++)
					{
						if (MinecraftServer.getServer().worldServers[i] != null)
						{
							if (MinecraftServer.getServer().worldServers[i].provider.dimensionId == dim)
							{
								world = MinecraftServer.getServer().worldServers[i];
							}
						}
					}
					TileEntityGPU tile = (TileEntityGPU) world.getBlockTileEntity(x, y, z);
					{
						ByteArrayDataOutput out = ByteStreams.newDataOutput();
						out.writeByte(NET_GPUINIT);
						out.writeInt(x);
						out.writeInt(y);
						out.writeInt(z);
						out.writeInt(tile.gpu.color.getRGB());
						double[] matrix = new double[6];
						tile.gpu.transform.getMatrix(matrix);
						writeMatrix(out,matrix);
						Iterator<AffineTransform> it = tile.gpu.transformStack.iterator();
						out.writeInt(tile.gpu.transformStack.size());
						while (it.hasNext())
						{
							it.next().getMatrix(matrix);
							writeMatrix(out,matrix);
						}
						PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload("CCLights2", out.toByteArray()), player);
					}
					for (int i = 0; i<tile.gpu.textures.length; i++)
					{
						if (tile.gpu.textures[i] != null)
						{
							sendTextures(player,tile.gpu.textures[i],i,x,y,z);
						}
					}
				}
				else
				{
					World world = CCLights2.proxy.getClientWorld();
					TileEntityGPU tile = (TileEntityGPU) world.getBlockTileEntity(x, y, z);
					if (tile == null) return;
					if (tile.gpu == null) return;
					recTexture(dat, tile);
				}
				break;
			}
			case (NET_GPUMOUSE):
			{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				int dim = dat.readInt();
				World world = null;
				for (int i = 0; i<MinecraftServer.getServer().worldServers.length; i++)
				{
					if (MinecraftServer.getServer().worldServers[i] != null)
					{
						if (MinecraftServer.getServer().worldServers[i].provider.dimensionId == dim)
						{
							world = MinecraftServer.getServer().worldServers[i];
						}
					}
				}
				TileEntityMonitor mtile = (TileEntityMonitor) world.getBlockTileEntity(x, y, z);
				int cmd = dat.readInt();
				switch(cmd)
				{
					case 0:
					{
						//MouseStart//
						int button = dat.readInt();
						int mx = dat.readInt();
						int my = dat.readInt();
						for (GPU g : mtile.mon.gpu)
						{
							TileEntityGPU tile = g.tile;
							if (tile != null) tile.startClick(player, button, mx, my);
						}
						break;
					}
					case 1:
					{
						//MouseMove//
						int mx = dat.readInt();
						int my = dat.readInt();
						for (GPU g : mtile.mon.gpu)
						{
							TileEntityGPU tile = g.tile;
							if (tile != null) tile.moveClick(player, mx, my);
						}
						break;
					}
					case 2:
					{
						//MouseEnd//
						for (GPU g : mtile.mon.gpu)
						{
							TileEntityGPU tile = g.tile;
							if (tile != null) tile.endClick(player);
						}
						break;
					}
				}
				break;
			}
			case (NET_GPUTILE):
			{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				TileEntityBigMonitor tile = (TileEntityBigMonitor) CCLights2.proxy.getClientWorld().getBlockTileEntity(x, y, z);
				if (tile != null)
				{
					tile.handleUpdatePacket(dat);
				}
				break;
			}
			case(NET_LIGHT):{
				int x = dat.readInt();
				int y = dat.readInt();
				int z = dat.readInt();
				World world = Minecraft.getMinecraft().theWorld;
				TileEntityaAdvancedlight tile = (TileEntityaAdvancedlight) world.getBlockTileEntity(x, y, z);
				if(tile != null){	
				TileEntityaAdvancedlight ntile = (TileEntityaAdvancedlight) tile;
					ntile.r = dat.readFloat();
					ntile.g = dat.readFloat();
					ntile.b = dat.readFloat();
				}
			}
		}
	}
	
	public void recTexture(ByteArrayDataInput dat, TileEntityGPU tile)
	{
		GPU gpu = tile.gpu;
		int id = dat.readInt();
		int w = dat.readInt();
		int h = dat.readInt();
		if (gpu.textures[id] == null)
		{
			gpu.textures[id] = new Texture(w, h);
		}
		Texture tex = gpu.textures[id];
		if (tex.getWidth() != w || tex.getHeight() != h)
		{
			gpu.textures[id] = new Texture(w, h);
			tex = gpu.textures[id];
		}
		int[] arr = new int[dat.readInt()];
		for (int i=0; i<arr.length; i++)
		{
			arr[i] = dat.readInt();
		}
		CCLights2.debug(w+","+h);
		tex.img.setRGB(0, 0, w, h, arr, 0, w);
	}
	
	public void sendTextures(Player whom, Texture tex, int id, int x, int y, int z)
	{
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
    	DataOutputStream outputStream = new DataOutputStream(bos);
    	try {
    		outputStream.writeByte(NET_GPUDOWNLOAD);
			outputStream.writeInt(x);
			outputStream.writeInt(y);
			outputStream.writeInt(z);
			outputStream.writeInt(id);
			outputStream.writeInt(tex.getWidth());
			outputStream.writeInt(tex.getHeight());
			int[] arr = new int[tex.getWidth()*tex.getHeight()*4];
			tex.img.getRGB(0, 0, tex.getWidth(), tex.getHeight(), arr, 0, tex.getWidth());
			outputStream.writeInt(arr.length);
			for (int i=0; i<arr.length; i++)
			{
				outputStream.writeInt(arr[i]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
    	packet.data = bos.toByteArray();
    	packet.length = bos.size();
    	//System.out.println(packet.length);
    	PacketSplitter.sendPacketToPlayer(packet, whom);
	}
	
	public void writeMatrix(ByteArrayDataOutput out, double[] matrix)
	{
		for (int i = 0; i<matrix.length; i++)
		{
			out.writeDouble(matrix[i]);
		}
	}
	
	public void readMatrix(ByteArrayDataInput dat, double[] matrix)
	{
		for (int i = 0; i<matrix.length; i++)
		{
			matrix[i] = dat.readDouble();
		}
	}
}
