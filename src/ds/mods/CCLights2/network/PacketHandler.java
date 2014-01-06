package ds.mods.CCLights2.network;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayDeque;

import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import dan200.computer.api.IComputerAccess;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.ClientDrawThread;
import ds.mods.CCLights2.block.tileentity.TileEntityAdvancedlight;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Texture;

public class PacketHandler implements IPacketHandler {
	//"GPUDrawlist", "GPUEvent", "GPUDownload", "GPUMouse", "GPUKey", "GPUTile","LIGHT"

	public static final byte NET_GPUDRAWLIST = 0;
	public static final byte NET_GPUEVENT = 1;
	public static final byte NET_GPUDOWNLOAD= 2;
	public static final byte NET_GPUMOUSE = 3;
	public static final byte NET_GPUKEY = 4;
	public static final byte NET_GPUTILE = 5;
	public static final byte NET_GPUINIT = 6;
	public static final byte NET_LIGHT = 7;
	
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
		ByteArrayDataInput dat = null;
		try {
			byte[] data = PacketChunker.instance.getBytes(packet);
			if (data != null) { // data is now the full, combined data
				dat = ByteStreams.newDataInput(data);
				byte typ = dat.readByte();
				switch (typ)
				{
				case (NET_GPUDRAWLIST):
				{
					int x = dat.readInt();
					int y = dat.readInt();
					int z = dat.readInt();
					int len = dat.readInt();
					TileEntityGPU tile = (TileEntityGPU) ClientProxy.getClientWorld().getBlockTileEntity(x, y, z);
					if (tile != null)
					{
						GPU gpu = tile.gpu;
						//TODO> alekso56: I might put clientside drawing in another thread so that Minecraft doesn't get stalled when a graphically intensive packet comes
						int[] most = new int[30];
						for (int i = 0; i<len; i++)
						{
							DrawCMD cmd = new DrawCMD();
							cmd.cmd = dat.readInt();
							most[cmd.cmd+1]++;
							int lent = dat.readInt();
							int step = dat.readInt();
							cmd.args = new double[lent];
							for (int g = step; g<lent; g = g++)
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
					TileEntityGPU tile = (TileEntityGPU) ClientProxy.getClientWorld().getBlockTileEntity(x, y, z);
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
					if(FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER){
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
						PacketSenders.sendPacketToPlayer(x, y, z, tile,player);
						for (int i = 0; i<tile.gpu.textures.length; i++)
						{
							if (tile.gpu.textures[i] != null)
							{
								PacketSenders.sendTextures(player,tile.gpu.textures[i],i,x,y,z);
							}
						}
					}
					else{
						World world = ClientProxy.getClientWorld();
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
					TileEntityExternalMonitor tile = (TileEntityExternalMonitor) ClientProxy.getClientWorld().getBlockTileEntity(x, y, z);
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
					World world = ClientProxy.getClientWorld();
					TileEntityAdvancedlight tile = (TileEntityAdvancedlight) world.getBlockTileEntity(x, y, z);
					if(tile != null){	
						TileEntityAdvancedlight ntile = (TileEntityAdvancedlight) tile;
						ntile.r = dat.readFloat();
						ntile.g = dat.readFloat();
						ntile.b = dat.readFloat();
					}
				}
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public static void recTexture(ByteArrayDataInput dat, TileEntityGPU tile)
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
	
	public void readMatrix(ByteArrayDataInput dat, double[] matrix)
	{
		for (int i = 0; i<matrix.length; i++)
		{
			matrix[i] = dat.readDouble();
		}
	}
}
