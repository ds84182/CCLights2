package ds.mods.CCLights2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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
import ds.mods.CCLights2.block.tileentity.MonitorBase;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;

public class PacketHandler implements IPacketHandler {

	@Override
	public void onPacketData(INetworkManager manager,
			Packet250CustomPayload packet, Player player) {
		ByteArrayDataInput dat = ByteStreams.newDataInput(packet.data);
		if (packet.channel.equals("GPUDrawlist"))
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
					tile.gpu.processCommand(cmd);
				}
				//tile.wait2 = false;
			}
		}
		else if (packet.channel.equals("GPUTexture"))
		{
			int x = dat.readInt();
			int y = dat.readInt();
			int z = dat.readInt();
			TileEntityGPU tile = (TileEntityGPU) CCLights2.proxy.getClientWorld().getBlockTileEntity(x, y, z);
			if (tile != null)
			{
				GPU gpu = tile.gpu;
				int com = dat.readInt();
				switch (com)
				{
					case 0:
					{
						//New Texture//
						int slot = dat.readInt();
						int width = dat.readInt();
						int height = dat.readInt();
						gpu.textures[slot] = new Texture(width, height);
						break;
					}
					case 1:
					{
						//Delete Texture//
						int slot = dat.readInt();
						gpu.textures[slot] = null;
						break;
					}
				}
			}
		}
		else if (packet.channel.equals("GPUEvent"))
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
					for (int i = 0; i<6; i++)
					{
						if (tile.comp[i] != null)
						{
							tile.comp[i].queueEvent(event, args);
						}
					}
				}
			}
		}
		else if (packet.channel.equals("GPUDownload"))
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
		}
		else if (packet.channel.equals("GPUMouse"))
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
		}
		else if (packet.channel.equals("GPUTile"))
		{
			int x = dat.readInt();
			int y = dat.readInt();
			int z = dat.readInt();
			TileEntityBigMonitor tile = (TileEntityBigMonitor) CCLights2.proxy.getClientWorld().getBlockTileEntity(x, y, z);
			if (tile != null)
			{
				tile.handleUpdatePacket(dat);
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
			packet.channel = "GPUDownload";
			ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
	    	DataOutputStream outputStream = new DataOutputStream(bos);
	    	try {
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	packet.data = bos.toByteArray();
	    	packet.length = bos.size();
	    	//System.out.println(packet.length);
	    	PacketDispatcher.sendPacketToPlayer(packet, whom);
		}
	}
}
