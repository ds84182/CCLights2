package ds.mods.CCLights2.network;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;

import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.ChatAllowedCharacters;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.Texture;

public class PacketSenders {

	public static void sendPacketsNow(Deque<DrawCMD> drawlist,TileEntityGPU tile) {
		if (tile == null)
			throw new IllegalArgumentException("GPU cannot send packet without Tile Entity!");
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUDRAWLIST);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		outputStream.writeInt(drawlist.size());
		while (!drawlist.isEmpty()) {
			DrawCMD c = drawlist.removeLast();
			outputStream.writeInt(c.cmd);
			int length = 0;
			if(c.args.length >= 4085){
				int repeats = 4085/c.args.length;
				length = 4085;
				for(int l = 0; l < repeats; l++){
				for (int g = 0; g < length; g++) {
					outputStream.writeDouble(c.args[g]);
				}
				}
			}
			else{
			int lenght = c.args.length;
			outputStream.writeInt(lenght);
			for (int g = 0; g < length; g++) {
				outputStream.writeDouble(c.args[g]);
			}
			}
		}
		packet.data = outputStream.toByteArray();
		packet.length = packet.data.length;
		PacketDispatcher.sendPacketToAllAround(tile.xCoord, tile.yCoord,
				tile.zCoord, 4096D, tile.worldObj.provider.dimensionId, packet);
	}

	public static void GPUEvent(int par1, int par2, TileEntityMonitor tile,int wheel) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayDataOutput out = ByteStreams.newDataOutput();

		out.writeByte(PacketHandler.NET_GPUEVENT);
		out.writeInt(tile.xCoord);
		out.writeInt(tile.yCoord);
		out.writeInt(tile.zCoord);
		out.writeInt(tile.worldObj.provider.dimensionId);
		out.writeUTF("monitor_scroll");
		out.writeInt(3);

		out.writeInt(0);
		out.writeInt(par1);

		out.writeInt(0);
		out.writeInt(par2);

		out.writeInt(0);
		out.writeInt(wheel / 120);

		packet.data = out.toByteArray();
		packet.length = packet.data.length;
		PacketDispatcher.sendPacketToServer(packet);
	}

	public static void sendPacketToPlayer(int x, int y, int z,TileEntityGPU tile, Player player) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeByte(PacketHandler.NET_GPUINIT);
		out.writeInt(x);
		out.writeInt(y);
		out.writeInt(z);
		out.writeInt(tile.gpu.color.getRGB());
		double[] matrix = new double[6];
		tile.gpu.transform.getMatrix(matrix);
		writeMatrix(out, matrix);
		Iterator<AffineTransform> it = tile.gpu.transformStack.iterator();
		out.writeInt(tile.gpu.transformStack.size());
		while (it.hasNext()) {
			it.next().getMatrix(matrix);
			writeMatrix(out, matrix);
		}
		PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload("CCLights2", out.toByteArray()), player);

	}

	public static void sendTextures(Player whom, Texture tex, int id, int x,int y, int z) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		DataOutputStream outputStream = new DataOutputStream(bos);
		try {
			outputStream.writeByte(PacketHandler.NET_GPUDOWNLOAD);
			outputStream.writeInt(x);
			outputStream.writeInt(y);
			outputStream.writeInt(z);
			outputStream.writeInt(id);
			outputStream.writeInt(tex.getWidth());
			outputStream.writeInt(tex.getHeight());
			int[] arr = new int[tex.getWidth() * tex.getHeight() * 4];
			tex.img.getRGB(0, 0, tex.getWidth(), tex.getHeight(), arr, 0,
					tex.getWidth());
			outputStream.writeInt(arr.length);
			for (int i = 0; i < arr.length; i++) {
				outputStream.writeInt(arr[i]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		packet.data = bos.toByteArray();
		packet.length = bos.size();
		// System.out.println(packet.length);
		PacketDispatcher.sendPacketToPlayer(packet, whom);
	}

	public static void mouseEvent(int mx, int my, int par3,TileEntityMonitor tile) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		DataOutputStream outputStream = new DataOutputStream(bos);
		try {
			outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
			outputStream.writeInt(tile.xCoord);
			outputStream.writeInt(tile.yCoord);
			outputStream.writeInt(tile.zCoord);
			outputStream.writeInt(tile.worldObj.provider.dimensionId);
			outputStream.writeInt(0);
			outputStream.writeInt(par3);
			outputStream.writeInt(mx);
			outputStream.writeInt(my);
		} catch (IOException e) {
			e.printStackTrace();
		}
		packet.data = bos.toByteArray();
		packet.length = bos.size();
		PacketDispatcher.sendPacketToServer(packet);
	}

	public static void mouseEventMove(int mx, int my, TileEntityMonitor tile) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		DataOutputStream outputStream = new DataOutputStream(bos);
		try {
			outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
			outputStream.writeInt(tile.xCoord);
			outputStream.writeInt(tile.yCoord);
			outputStream.writeInt(tile.zCoord);
			outputStream.writeInt(tile.worldObj.provider.dimensionId);
			outputStream.writeInt(1);
			outputStream.writeInt(mx);
			outputStream.writeInt(my);
		} catch (IOException e) {
			e.printStackTrace();
		}
		packet.data = bos.toByteArray();
		packet.length = bos.size();
		PacketDispatcher.sendPacketToServer(packet);
	}

	public static void mouseEventUp(TileEntityMonitor tile) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		DataOutputStream outputStream = new DataOutputStream(bos);
		try {
			outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
			outputStream.writeInt(tile.xCoord);
			outputStream.writeInt(tile.yCoord);
			outputStream.writeInt(tile.zCoord);
			outputStream.writeInt(tile.worldObj.provider.dimensionId);
			outputStream.writeInt(2);
		} catch (IOException e) {
			e.printStackTrace();
		}
		packet.data = bos.toByteArray();
		packet.length = bos.size();
		PacketDispatcher.sendPacketToServer(packet);
	}

	public static void sendKeyEvent(char par1, int par2, TileEntityMonitor tile) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		DataOutputStream outputStream = new DataOutputStream(bos);
		try {
			outputStream.writeByte(PacketHandler.NET_GPUEVENT);
			outputStream.writeInt(tile.xCoord);
			outputStream.writeInt(tile.yCoord);
			outputStream.writeInt(tile.zCoord);
			outputStream.writeInt(tile.worldObj.provider.dimensionId);
			outputStream.writeUTF("key");
			outputStream.writeInt(1);
			outputStream.writeInt(0);
			outputStream.writeInt(par2);
		} catch (IOException e) {
			e.printStackTrace();
		}
		packet.data = bos.toByteArray();
		packet.length = bos.size();
		PacketDispatcher.sendPacketToServer(packet);

		if (ChatAllowedCharacters.isAllowedCharacter(par1)) {
			packet = new Packet250CustomPayload();
			packet.channel = "CCLights2";
			bos = new ByteArrayOutputStream(8);
			outputStream = new DataOutputStream(bos);
			try {
				outputStream.writeByte(PacketHandler.NET_GPUEVENT);
				outputStream.writeInt(tile.xCoord);
				outputStream.writeInt(tile.yCoord);
				outputStream.writeInt(tile.zCoord);
				outputStream.writeInt(tile.worldObj.provider.dimensionId);
				outputStream.writeUTF("char");
				outputStream.writeInt(1);
				outputStream.writeInt(2);
				outputStream.writeChar(par1);
			} catch (IOException e) {
				e.printStackTrace();
			}
			packet.data = bos.toByteArray();
			packet.length = bos.size();
			PacketDispatcher.sendPacketToServer(packet);
		}
	}

	public static void writeMatrix(ByteArrayDataOutput out, double[] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			out.writeDouble(matrix[i]);
		}
	}

}
