package ds.mods.CCLights2.network;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.IWritableMount;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.ClientDrawThread;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityAdvancedlight;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.serialize.Serialize;

public class PacketHandler implements IPacketHandler,IConnectionHandler {
	static final byte NET_GPUDRAWLIST = 0;
	static final byte NET_GPUEVENT = 1;
	static final byte NET_GPUDOWNLOAD = 2;
	static final byte NET_GPUMOUSE = 3;
	static final byte NET_GPUKEY = 4;
	static final byte NET_GPUTILE = 5;
	static final byte NET_GPUINIT = 6;
	static final byte NET_LIGHT = 7;
	static final byte NET_SPLITPACKET = 8;
	static final byte NET_SYNC = 9;
	static final byte NET_SCREENSHOT = 10;
	static boolean doThreadding = true;
	static ClientDrawThread thread;
	{
		if (doThreadding) {
			thread = new ClientDrawThread();
			thread.start();
		}
	}

	@Override
	public void onPacketData(INetworkManager manager,
			Packet250CustomPayload packet, Player player) {
		ByteArrayDataInput maindat = ByteStreams.newDataInput(packet.data);
		byte typ = maindat.readByte();
		if (typ == NET_SPLITPACKET) {
			try {
				byte[] data = PacketChunker.instance.getBytes(packet);
				if (data != null) { // data is now the full, combined data

					// im crap at gzip ;_; but hey! it works!
					ByteArrayInputStream input = new ByteArrayInputStream(data);
					GZIPInputStream zipStream = new GZIPInputStream(input);
					ByteArrayOutputStream bo = new ByteArrayOutputStream();
					while (zipStream.available() > 0) {
						bo.write(zipStream.read());
					}
					ByteArrayDataInput dat = ByteStreams.newDataInput(bo
							.toByteArray());
					zipStream.close();
					input.close();
					typ = dat.readByte();
					if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
						ServerSide(typ, dat, player);
					} else if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
						ClientSide(typ, dat);
					}
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else {
			if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
				ServerSide(typ, maindat, player);
			} else if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
				ClientSide(typ, maindat);
			}
		}
	}

	public static void ServerSide(byte typ, ByteArrayDataInput PacketData,
			Player player) {
		EntityPlayerMP playr = (EntityPlayerMP) player;
		switch (typ) {
		case (NET_GPUMOUSE): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityMonitor mtile = (TileEntityMonitor) MinecraftServer.getServer().worldServers[playr.dimension].getBlockTileEntity(x, y, z);
			if (mtile != null) {
				int cmd = PacketData.readInt();
				switch (cmd) {
				case 0: {
					// MouseStart//
					int button = PacketData.readInt();
					int mx = PacketData.readInt();
					int my = PacketData.readInt();
					for (GPU g : mtile.mon.gpu) {
						TileEntityGPU tile = g.tile;
						if (tile != null)
							tile.startClick(player, button, mx, my);
					}
					break;
				}
				case 1: {
					// MouseMove//
					int mx = PacketData.readInt();
					int my = PacketData.readInt();
					for (GPU g : mtile.mon.gpu) {
						TileEntityGPU tile = g.tile;
						if (tile != null)
							tile.moveClick(player, mx, my);
					}
					break;
				}
				case 2: {
					// MouseEnd//
					for (GPU g : mtile.mon.gpu) {
						TileEntityGPU tile = g.tile;
						if (tile != null)
							tile.endClick(player);
					}
				}
				}
			}
			break;
		}
		case (NET_GPUEVENT): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityMonitor mtile = (TileEntityMonitor) MinecraftServer.getServer().worldServers[playr.dimension].getBlockTileEntity(x, y, z);
			if (mtile != null) {
				String event = PacketData.readUTF();
				int len = PacketData.readInt();
				Object[] args = new Object[len];
				for (int i1 = 0; i1 < len; i1++) {
					int type = PacketData.readInt();
					switch (type) {
					case 0: {
						args[i1] = PacketData.readInt();
						break;
					}
					case 1: {
						args[i1] = PacketData.readUTF();
						break;
					}
					case 2: {
						args[i1] = String.valueOf(PacketData
								.readChar());
						break;
					}
					}
				}
				for (GPU g : mtile.mon.gpu) {
					TileEntityGPU tile = g.tile;
					if (tile != null) {
						for (IComputerAccess c : tile.comp)
							if (c != null) {
								c.queueEvent(event, args);
							}
					}
				}
			}
			break;
		}
		case (NET_GPUDOWNLOAD): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			CCLights2.debug("Got DL packet from client!");
			TileEntityGPU tile = (TileEntityGPU) MinecraftServer.getServer().worldServers[playr.dimension].getBlockTileEntity(x, y, z);
			if (tile != null) {
				PacketSenders.sendPacketToPlayer(x, y, z, tile,player);
				for (int i1 = 0; i1 < tile.gpu.textures.length; i1++) {
					if (tile.gpu.textures[i1] != null) {
						PacketSenders.sendTextures(player,tile.gpu.textures[i1], i1, x, y, z);
					}
				}
			}
			break;
		}
		case (NET_SCREENSHOT): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			int len = PacketData.readInt();
			byte[] arr = new byte[len];
			PacketData.readFully(arr);
			CCLights2.debug("Got Screenshot packet from client!");
			TileEntityTTrans tile = (TileEntityTTrans) MinecraftServer.getServer().worldServers[playr.dimension].getBlockTileEntity(x, y, z);
			if (tile != null) {//CCLights2.debug("found TTtrans to submit image to!");
				HashMap<Double, Double> table = new HashMap<Double, Double>();
				ByteArrayInputStream in = new ByteArrayInputStream(arr);
				Double at = 1D;
				int r;
				while ((r = in.read()) != -1)
				{
					table.put(at++,(double) r);
				}
				
				for (GPU g : tile.mon.gpu) {
					TileEntityGPU gtile = g.tile;
					if (gtile != null) {
						for (IComputerAccess c : gtile.comp)
							if (c != null) {
								/*File directory = new File(CCLights2.proxy.getWorldDir(playr.worldObj)+"//computer//"+c.getID()+"//screenshot.jpg");
								try {
									ImageIO.write(ImageIO.read(in),"jpg",directory);
								} catch (IOException e) {
									e.printStackTrace();
								}*/
								//BAD ALEKOS! NO MASS FILE WRITES!
								c.queueEvent("tablet_image",new Object[]{table});
								CCLights2.debug("QUEUED EVENT");
							}
					}
				}
			}
		}
		}
	}

	public static void ClientSide(byte typ, ByteArrayDataInput PacketData) {
		switch (typ) {
		case (NET_GPUDRAWLIST): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityGPU tile = (TileEntityGPU) ClientProxy.getClientWorld()
					.getBlockTileEntity(x, y, z);
			if (tile != null) {
				int len = PacketData.readInt();
				GPU gpu = tile.gpu;
				// int[] most = new int[30];
				for (int i = 0; i < len; i++) {
					DrawCMD cmd = new DrawCMD();
					cmd.cmd = PacketData.readInt();
					// most[cmd.cmd + 1]++;
					int lent = PacketData.readInt();
					cmd.args = new Object[lent];
					for (int g = 0; g < lent; g++) {
						if (PacketData.readByte() == -1)
						{
							int count = PacketData.readInt();
							cmd.args[g] = new Object[count];
							Object[] arr = (Object[]) cmd.args[g];
							for (int e = 0; e<count; e++)
							{
								arr[e] = Serialize.unserialize(PacketData);
							}
						}
						else
						{
							PacketData.skipBytes(-1);
							cmd.args[g] = Serialize.unserialize(PacketData);
						}
					}
					if (!doThreadding)
						try {
							tile.gpu.processCommand(cmd);
						} catch (Exception e) {
							// MEH.
							e.printStackTrace();
						}
					else {
						if (!thread.isAlive()) {
							CCLights2
							.debug("The client draw thread died, restarting");
							thread = new ClientDrawThread();
							thread.start();
						}
						if (thread.draws.get(tile.gpu) == null) {
							thread.draws.put(tile.gpu,
									new ArrayDeque<DrawCMD>());
						}
						thread.draws.get(tile.gpu).addLast(cmd);
					}
				}
				/*
				 * int n = -1; int ind = 0; for (int i = 0; i < most.length;
				 * i++) { if (n < most[i]) { n = most[i]; ind = i; } }
				 * System.out
				 * .println("Most used drawcmd: "+(ind-1)+" with "+n+" uses");
				 */
			}
			break;
		}
		case (NET_GPUINIT): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityGPU tile = (TileEntityGPU) ClientProxy.getClientWorld()
					.getBlockTileEntity(x, y, z);
			if (tile == null)
				return;
			if (tile.gpu == null)
				return;
			tile.gpu.color = new Color(PacketData.readInt(), true);
			double[] matrix = new double[6];
			readMatrix(PacketData, matrix);
			tile.gpu.transform = new AffineTransform(matrix);
			tile.gpu.transformStack.clear();
			for (int i = 0; i < PacketData.readInt(); i++) {
				readMatrix(PacketData, matrix);
				tile.gpu.transformStack.push(new AffineTransform(matrix));
			}
			break;
		}
		case (NET_GPUDOWNLOAD): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityGPU tile = (TileEntityGPU) ClientProxy.getClientWorld().getBlockTileEntity(x, y,z);
			if (tile == null || tile.gpu == null)
				return;
			recTexture(PacketData, tile);
			break;
		}
		case (NET_GPUTILE): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityExternalMonitor tile = (TileEntityExternalMonitor) ClientProxy
					.getClientWorld().getBlockTileEntity(x, y, z);
			if (tile != null) {
				tile.handleUpdatePacket(PacketData);
			}
			break;
		}
		case (NET_LIGHT): {
			int x = PacketData.readInt();
			int y = PacketData.readInt();
			int z = PacketData.readInt();
			TileEntityAdvancedlight tile = (TileEntityAdvancedlight) ClientProxy.getClientWorld()
					.getBlockTileEntity(x, y, z);
			if (tile != null) {
				TileEntityAdvancedlight ntile = tile;
				ntile.r = PacketData.readFloat();
				ntile.g = PacketData.readFloat();
				ntile.b = PacketData.readFloat();
			}
			break;
		}
		case(NET_SYNC):{}
		}
	}

	public static void recTexture(ByteArrayDataInput dat, TileEntityGPU tile) {
		GPU gpu = tile.gpu;
		int id = dat.readInt();
		int w = dat.readInt();
		int h = dat.readInt();
		if (gpu.textures[id] == null) {
			gpu.textures[id] = new Texture(w, h);
		}
		Texture tex = gpu.textures[id];
		if (tex.getWidth() != w || tex.getHeight() != h) {
			gpu.textures[id] = new Texture(w, h);
			tex = gpu.textures[id];
		}
		int[] arr = new int[dat.readInt()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = dat.readInt();
		}
		CCLights2.debug(w + "," + h);
		tex.img.setRGB(0, 0, w, h, arr, 0, w);
	}

	public static void readMatrix(ByteArrayDataInput dat, double[] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			matrix[i] = dat.readDouble();
		}
	}

	@Override
	public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager)
	{
		if(Config.monitorSize[0] != 256 || Config.monitorSize[1] != 144){
			if(MinecraftServer.getServer().isDedicatedServer()){
				PacketSenders.SYNC(Config.monitorSize[0], Config.monitorSize[1],player);}
		}
	}
	@Override
	public void clientLoggedIn(NetHandler clientHandler,INetworkManager manager, Packet1Login login) 
	{
		if(Minecraft.getMinecraft().isSingleplayer())
		{
			CCLights2.debug("Singleplayer detected, sync not needed");
		}
		else{
			Config.setDefaults();
			CCLights2.debug("PREP'd for SYNC");
		}
	}

	public String connectionReceived(NetLoginHandler netHandler,INetworkManager manager) {return null;}
	public void connectionOpened(NetHandler netClientHandler, String server,int port, INetworkManager manager) {}
	public void connectionOpened(NetHandler netClientHandler,MinecraftServer server, INetworkManager manager) {}
	public void connectionClosed(INetworkManager manager) {}
}