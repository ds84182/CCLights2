package ds.mods.CCLights2.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import dan200.computer.api.IComputerAccess;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.DrawCMD;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.Texture;
import ds.mods.CCLights2.block.tileentity.MonitorBase;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;

public class PacketHandler implements IPacketHandler {
	//"GPUDrawlist", "GPUEvent", "GPUDownload", "GPUMouse", "GPUKey", "GPUTile"
	public static final byte NET_SPLITPACKET = -1;
	public static final byte NET_GPUDRAWLIST = 0;
	public static final byte NET_GPUEVENT = 1;
	public static final byte NET_GPUDOWNLOAD = 2;
	public static final byte NET_GPUMOUSE = 3;
	public static final byte NET_GPUKEY = 4;
	public static final byte NET_GPUTILE = 5;
	
	public HashMap<Short,SplitPacket> splitPackets = new HashMap<Short,SplitPacket>();
	
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
						System.out.println("Split packet finished.");
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
					//gpu.drawlist.clear(); //Dunno why this is here...
					//while (tile.wait) {};
					//tile.wait2 = true;
					for (int i = 0; i<len; i++)
					{
						DrawCMD cmd = new DrawCMD();
						cmd.cmd = dat.readInt();
						int lent = dat.readInt();
						cmd.args = new int[lent];
						for (int g = 0; g<lent; g++)
						{
							cmd.args[g] = dat.readInt();
						}
						try {
							tile.gpu.processCommand(cmd);
						} catch (Exception e) {
							//MEH.
							e.printStackTrace();
						}
					}
					//tile.wait2 = false;
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
				MonitorBase mtile = (MonitorBase) world.getBlockTileEntity(x, y, z);
				for (GPU g : mtile.mon.gpu)
				{
					TileEntityGPU tile = g.tile;
					if (tile != null)
					{
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
						for (IComputerAccess c : tile.comp)
							if (c != null)
							{
								c.queueEvent(event, args);
							}
					}
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
					System.out.println("Got DL packet from client!");
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
					//Send 1/4 of the textures in each packet, or 2048//
					int texat = 0;
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
				MonitorBase mtile = (MonitorBase) world.getBlockTileEntity(x, y, z);
				for (GPU g : mtile.mon.gpu)
				{
					TileEntityGPU tile = g.tile;
					if (tile != null)
					{
						int cmd = dat.readInt();
						switch(cmd)
						{
							case 0:
							{
								//MouseStart//
								int button = dat.readInt();
								int mx = dat.readInt();
								int my = dat.readInt();
								tile.startClick(player, button, mx, my);
								break;
							}
							case 1:
							{
								//MouseMove//
								int mx = dat.readInt();
								int my = dat.readInt();
								tile.moveClick(player, mx, my);
								break;
							}
							case 2:
							{
								//MouseEnd//
								tile.endClick(player);
								break;
							}
						}
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
		}
	}
	
	public void recTexture(ByteArrayDataInput dat, TileEntityGPU tile)
	{
		GPU gpu = tile.gpu;
		int id = dat.readInt();
		int y = dat.readInt();
		int w = dat.readInt();
		int h = dat.readInt();
		int bpp = dat.readInt();
		if (gpu.textures[id] == null)
		{
			gpu.textures[id] = new Texture(w, h);
			gpu.textures[id].setBPP(bpp);
		}
		Texture tex = gpu.textures[id];
		tex.setTransparent(dat.readBoolean());
		tex.transparentColor = dat.readInt();
		
		for (int x = 0; x<w; x++)
		{
			switch (bpp)
			{
			case 1:
			{
				tex.plot(dat.readByte(), x, y);
				break;
			}
			case 2:
			{
				tex.plot(dat.readByte() | (dat.readByte()<<8), x, y);
				break;
			}
			case 4:
			{
				tex.plot(dat.readByte() | (dat.readByte()<<8) | (dat.readByte()<<16), x, y);
				break;
			}
			}
		}
	}
	
	public void sendTextures(Player whom, Texture tex, int id, int x, int y, int z)
	{
		for (int line = 0; line<tex.getHeight(); line++)
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
				outputStream.writeInt(line);
				outputStream.writeInt(tex.getWidth());
				outputStream.writeInt(tex.getHeight());
				outputStream.writeInt(tex.bpp);
				outputStream.writeBoolean(tex.isTransparent);
				outputStream.writeInt(tex.transparentColor);
				for (int col = 0; col < tex.getWidth(); col++)
				{
					int i = (line*tex.getWidth())+col;
					int data = tex.texture[i];
					switch (tex.bpp)
					{
					case 1:
					{
						outputStream.writeByte(data);
						break;
					}
					case 2:
					{
						outputStream.writeByte(data&0xFF);
						outputStream.writeByte((data>>8)&0xFF);
						break;
					}
					case 4:
					{
						outputStream.writeByte(data&0xFF);
						outputStream.writeByte((data>>8)&0xFF);
						outputStream.writeByte((data>>16)&0xFF);
						break;
					}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	packet.data = bos.toByteArray();
	    	packet.length = bos.size();
	    	//System.out.println(packet.length);
	    	PacketSplitter.sendPacketToPlayer(packet, whom);
		}
	}
}
