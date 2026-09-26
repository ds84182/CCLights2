package ds.mods.CCLights2.network;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketHandler.PacketMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

/**
 * Builders for every packet CCLights2 sends.
 */
public final class PacketSenders {
	private PacketSenders() {}

	/** Range within which clients receive draw commands. */
	public static final double DRAW_RANGE = 256.0D;

	private static ByteArrayDataOutput header(byte type, TileEntity tile) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeByte(type);
		out.writeInt(tile.xCoord);
		out.writeInt(tile.yCoord);
		out.writeInt(tile.zCoord);
		return out;
	}

	private static void toServer(ByteArrayDataOutput out) {
		try {
			for (PacketMessage p : PacketChunker.instance.createPackets(out.toByteArray())) {
				CCLights2.network.sendToServer(p);
			}
		} catch (IOException e) {
			CCLights2.logger.warn("Failed to send packet to server: " + e);
		}
	}

	private static void toPlayer(ByteArrayDataOutput out, EntityPlayer player) {
		if (!(player instanceof EntityPlayerMP)) return;
		try {
			for (PacketMessage p : PacketChunker.instance.createPackets(out.toByteArray())) {
				CCLights2.network.sendTo(p, (EntityPlayerMP) player);
			}
		} catch (IOException e) {
			CCLights2.logger.warn("Failed to send packet to " + player.getCommandSenderName() + ": " + e);
		}
	}

	private static void toAround(ByteArrayDataOutput out, TileEntity tile, double range) {
		try {
			TargetPoint point = new TargetPoint(tile.getWorldObj().provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord, range);
			for (PacketMessage p : PacketChunker.instance.createPackets(out.toByteArray())) {
				CCLights2.network.sendToAllAround(p, point);
			}
		} catch (IOException e) {
			CCLights2.logger.warn("Failed to broadcast packet: " + e);
		}
	}

	// ------------------------------------------------------------------ server -> client

	/** Flushes a GPU's pending draw commands to nearby clients. */
	public static void sendDrawList(Collection<DrawCMD> drawlist, TileEntityGPU tile) {
		if (drawlist.isEmpty()) return;
		ByteArrayDataOutput out = header(PacketProcessor.NET_GPUDRAWLIST, tile);
		out.writeInt(drawlist.size());
		for (DrawCMD c : drawlist) Serialize.writeCommand(out, c);
		toAround(out, tile, DRAW_RANGE);
	}

	/** Sends the complete GPU state to one player (on request, e.g. after the chunk loaded). */
	public static void sendGPUSync(TileEntityGPU tile, EntityPlayer player) {
		GPU gpu = tile.gpu;
		ByteArrayDataOutput out = header(PacketProcessor.NET_GPUSYNC, tile);
		synchronized (gpu) {
			gpu.state.write(out);
			out.writeInt(gpu.bindedSlot);
			int count = 0;
			for (int i = 0; i < gpu.textures.length; i++) if (gpu.textures[i] != null) count++;
			out.writeInt(count);
			for (int i = 0; i < gpu.textures.length; i++) {
				Texture t = gpu.textures[i];
				if (t == null) continue;
				out.writeInt(i);
				out.writeInt(t.getWidth());
				out.writeInt(t.getHeight());
				Serialize.write(out, t.getPixels(0, 0, t.getWidth(), t.getHeight()));
			}
			int shaderCount = 0;
			for (int i = 1; i < gpu.shaders.length; i++) if (gpu.shaders[i] != null) shaderCount++;
			out.writeInt(shaderCount);
			for (int i = 1; i < gpu.shaders.length; i++) {
				ShaderInstance sh = gpu.shaders[i];
				if (sh == null) continue;
				out.writeInt(i);
				Serialize.writeString(out, sh.source);
				Serialize.write(out, sh.values);
				Serialize.write(out, sh.samplers);
			}
		}
		toPlayer(out, player);
	}

	// ------------------------------------------------------------------ client -> server

	public static void requestGPUSync(TileEntityGPU tile) {
		toServer(header(PacketProcessor.NET_GPUDOWNLOAD, tile));
	}

	public static void mouseDown(int mx, int my, int button, TileEntityMonitor tile) {
		ByteArrayDataOutput out = header(PacketProcessor.NET_GPUMOUSE, tile);
		out.writeInt(0);
		out.writeInt(button);
		out.writeInt(mx);
		out.writeInt(my);
		toServer(out);
	}

	public static void mouseMove(int mx, int my, TileEntityMonitor tile) {
		ByteArrayDataOutput out = header(PacketProcessor.NET_GPUMOUSE, tile);
		out.writeInt(1);
		out.writeInt(mx);
		out.writeInt(my);
		toServer(out);
	}

	public static void mouseUp(TileEntityMonitor tile) {
		ByteArrayDataOutput out = header(PacketProcessor.NET_GPUMOUSE, tile);
		out.writeInt(2);
		toServer(out);
	}

	/** Raises a ComputerCraft event on the computers driving a monitor. */
	public static void event(TileEntityMonitor tile, String event, Object... args) {
		ByteArrayDataOutput out = header(PacketProcessor.NET_GPUEVENT, tile);
		Serialize.writeString(out, event);
		out.writeInt(args.length);
		for (Object a : args) Serialize.write(out, a);
		toServer(out);
	}

	public static void scroll(int mx, int my, int direction, TileEntityMonitor tile) {
		event(tile, "monitor_scroll", mx, my, direction);
	}

	public static void keyDown(int keyCode, boolean repeat, TileEntityMonitor tile) {
		event(tile, "key", keyCode, repeat);
	}

	public static void keyUp(int keyCode, TileEntityMonitor tile) {
		event(tile, "key_up", keyCode);
	}

	public static void charTyped(char c, TileEntityMonitor tile) {
		event(tile, "char", String.valueOf(c));
	}

	/** Scales a client screenshot to the transceiver's screen size and ships it as JPEG. */
	public static void screenshot(TileEntityTTrans tile, BufferedImage screenshot) {
		int w = tile.getMonitor().getWidth(), h = tile.getMonitor().getHeight();
		BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(screenshot, 0, 0, w, h, null);
		g.dispose();
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
			ImageWriteParam param = new JPEGImageWriteParam(Locale.getDefault());
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.6f);
			ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
			writer.setOutput(ios);
			writer.write(null, new IIOImage(scaled, null, null), param);
			ios.close();
			writer.dispose();
			byte[] jpeg = baos.toByteArray();
			ByteArrayDataOutput out = header(PacketProcessor.NET_SCREENSHOT, tile);
			out.writeInt(jpeg.length);
			out.write(jpeg);
			toServer(out);
		} catch (IOException e) {
			CCLights2.logger.warn("Failed to encode tablet screenshot: " + e);
		}
	}
}
