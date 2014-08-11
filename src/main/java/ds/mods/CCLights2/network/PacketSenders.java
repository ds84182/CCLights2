package ds.mods.CCLights2.network;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.ChatAllowedCharacters;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.serialize.Serialize;

public final class PacketSenders {

	public static void sendPacketsNow(Deque<DrawCMD> drawlist,
			TileEntityGPU tile) {
		if (tile == null) {
			throw new IllegalArgumentException(
					"GPU cannot send packet without Tile Entity!");
		}
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUDRAWLIST);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		outputStream.writeInt(drawlist.size());
		while (!drawlist.isEmpty()) {
			DrawCMD c = drawlist.removeLast();
			outputStream.writeInt(c.cmd.ordinal());
			outputStream.writeInt(c.args.length);
			for (int g = 0; g < c.args.length; g++) {
				Object v = c.args[g];
				if (v != null && v.getClass().isArray())
				{
					Object[] arr = (Object[]) v;
					outputStream.writeByte(-1);
					outputStream.writeInt(arr.length);
					for (int i=0; i<arr.length; i++)
					{
						Serialize.serialize(outputStream, arr[i]);
					}
				}
				else
				{
					outputStream.writeByte(0);
					Serialize.serialize(outputStream, v);
				}
			}
		}
		try {
			Packet[] packets = PacketChunker.instance.createPackets(
					"CCLights2", outputStream.toByteArray());

			for (int g = 0; g < packets.length; g++) {
				PacketDispatcher.sendPacketToAllAround(tile.xCoord,
						tile.yCoord, tile.zCoord, 4096.0D,
						tile.worldObj.provider.dimensionId, packets[g]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void GPUEvent(int par1, int par2, TileEntityMonitor tile,
			int wheel) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();

		out.writeByte(PacketHandler.NET_GPUEVENT);
		out.writeInt(tile.xCoord);
		out.writeInt(tile.yCoord);
		out.writeInt(tile.zCoord);
		out.writeUTF("monitor_scroll");
		out.writeInt(3);

		out.writeInt(0);
		out.writeInt(par1);

		out.writeInt(0);
		out.writeInt(par2);

		out.writeInt(0);
		out.writeInt(wheel / 120);
		createPacketAndSend(out);
	}

	public synchronized static void sendPacketToPlayer(int x, int y, int z,
			TileEntityGPU tile, Player player) {
		try {
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
			Packet[] packets = PacketChunker.instance.createPackets(
					"CCLights2", out.toByteArray());
			for (int g = 0; g < packets.length; g++) {
				PacketDispatcher.sendPacketToPlayer(packets[g], player);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void sendTextures(Player whom, Texture tex, int id, int x,
			int y, int z) {
		try {
			ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
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
			Packet[] packets = PacketChunker.instance.createPackets(
					"CCLights2", outputStream.toByteArray());
			for (int g = 0; g < packets.length; g++) {
				PacketDispatcher.sendPacketToPlayer(packets[g], whom);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void mouseEvent(int mx, int my, int par3,
			TileEntityMonitor tile) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		outputStream.writeInt(0);
		outputStream.writeInt(par3);
		outputStream.writeInt(mx);
		outputStream.writeInt(my);
		createPacketAndSend(outputStream);
	}

	public static void mouseEventMove(int mx, int my, TileEntityMonitor tile) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		outputStream.writeInt(1);
		outputStream.writeInt(mx);
		outputStream.writeInt(my);
		createPacketAndSend(outputStream);
	}

	public static void mouseEventUp(TileEntityMonitor tile) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		outputStream.writeInt(2);
		createPacketAndSend(outputStream);
	}

	public static void screenshot(TileEntityTTrans tile, BufferedImage screenshot) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_SCREENSHOT);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		Image scaledshot = screenshot.getScaledInstance(tile.mon.getWidth(), tile.mon.getHeight(), 1);
		BufferedImage ScaledScreenshot = new BufferedImage(scaledshot.getWidth(null), scaledshot.getHeight(null), BufferedImage.TYPE_INT_RGB);
		// Draw the image on to the buffered image
		Graphics2D bGr = ScaledScreenshot.createGraphics();
		bGr.drawImage(scaledshot, 0, 0, null);
		bGr.dispose();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			//ImageIO.write(ScaledScreenshot, "jpg", baos);
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
			ImageWriteParam iwparam = new JPEGImageWriteParam(Locale.getDefault());
			iwparam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			iwparam.setCompressionQuality(0.5f);
			writer.setOutput(ImageIO.createImageOutputStream(baos));
			writer.write(null, new IIOImage(ScaledScreenshot, null, null), iwparam);
			byte[] screenshotArray = baos.toByteArray();
			outputStream.writeInt(screenshotArray.length);
			outputStream.write(screenshotArray);

			Packet[] packets = PacketChunker.instance.createPackets("CCLights2", outputStream.toByteArray());
			for (int g = 0; g < packets.length; g++) {
				PacketDispatcher.sendPacketToServer(packets[g]);
			}
		} catch (IOException e1) {
			CCLights2.debug("failed to send screenshot packets");
		}
	}

	public static void sendKeyEvent(char par1, int par2, TileEntityMonitor tile) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUEVENT);
		outputStream.writeInt(tile.xCoord);
		outputStream.writeInt(tile.yCoord);
		outputStream.writeInt(tile.zCoord);
		outputStream.writeUTF("key");
		outputStream.writeInt(1);
		outputStream.writeInt(0);
		outputStream.writeInt(par2);
		createPacketAndSend(outputStream);

		if (ChatAllowedCharacters.isAllowedCharacter(par1)) {
			ByteArrayDataOutput outputStream1 = ByteStreams.newDataOutput();
			outputStream1.writeByte(PacketHandler.NET_GPUEVENT);
			outputStream1.writeInt(tile.xCoord);
			outputStream1.writeInt(tile.yCoord);
			outputStream1.writeInt(tile.zCoord);
			outputStream1.writeUTF("char");
			outputStream1.writeInt(1);
			outputStream1.writeInt(2);
			outputStream1.writeChar(par1);
			createPacketAndSend(outputStream1);
		}
	}

	public static void writeMatrix(ByteArrayDataOutput out, double[] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			out.writeDouble(matrix[i]);
		}
	}

	public synchronized static void ExternalMonitorUpdate(int xCoord,
			int yCoord, int zCoord, int dimId, int m_width, int m_height,
			int m_xIndex, int m_yIndex, int m_dir) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUTILE);
		outputStream.writeInt(xCoord);
		outputStream.writeInt(yCoord);
		outputStream.writeInt(zCoord);
		outputStream.writeInt(m_width);
		outputStream.writeInt(m_height);
		outputStream.writeInt(m_xIndex);
		outputStream.writeInt(m_yIndex);
		outputStream.writeInt(m_dir);
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		packet.data = outputStream.toByteArray();
		packet.length = packet.data.length;
		PacketDispatcher.sendPacketToAllAround(xCoord, yCoord, zCoord, 4096.0D,
				dimId, packet);
	}

	public static void GPUDOWNLOAD(int xCoord, int yCoord, int zCoord) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_GPUDOWNLOAD);
		outputStream.writeInt(xCoord);
		outputStream.writeInt(yCoord);
		outputStream.writeInt(zCoord);
		createPacketAndSend(outputStream);
	}

	public static void createPacketAndSend(ByteArrayDataOutput mergeStream) {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		packet.data = mergeStream.toByteArray();
		packet.length = packet.data.length;
		PacketDispatcher.sendPacketToServer(packet);
	}

	public static void SYNC(int monitorWidth, int monitorHeight,Player player) {
		ByteArrayDataOutput outputStream = ByteStreams.newDataOutput();
		outputStream.writeByte(PacketHandler.NET_SYNC);
		outputStream.writeShort(monitorWidth);
		outputStream.writeShort(monitorHeight);
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		packet.data = outputStream.toByteArray();
		packet.length = packet.data.length;
		PacketDispatcher.sendPacketToPlayer(packet, player);
	}

}
