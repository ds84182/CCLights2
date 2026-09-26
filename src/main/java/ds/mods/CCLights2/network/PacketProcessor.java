package ds.mods.CCLights2.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.relauncher.Side;
import dan200.computercraft.api.peripheral.IComputerAccess;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.Texture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Decodes CCLights2 packets. Runs on the game thread of the receiving side.
 */
public final class PacketProcessor {
	private PacketProcessor() {}

	/** Server to client: a batch of draw commands for one GPU. */
	static final byte NET_GPUDRAWLIST = 0;
	/** Client to server: a ComputerCraft event raised from a monitor GUI (keys, scroll). */
	static final byte NET_GPUEVENT = 1;
	/** Client to server: please send me the full state of this GPU. */
	static final byte NET_GPUDOWNLOAD = 2;
	/** Client to server: mouse down/move/up on a monitor. */
	static final byte NET_GPUMOUSE = 3;
	/** Server to client: full GPU state (draw state and every texture). */
	static final byte NET_GPUSYNC = 4;
	/** Either direction: piece of a compressed multi-part message. */
	static final byte NET_SPLITPACKET = 8;
	/** Client to server: JPEG screenshot taken for a tablet transceiver. */
	static final byte NET_SCREENSHOT = 10;

	public static void handle(Side side, byte[] raw, EntityPlayer player) {
		try {
			byte[] data = raw;
			if (raw.length > 0 && raw[0] == NET_SPLITPACKET) {
				String sender = side == Side.SERVER ? (player == null ? "?" : player.getCommandSenderName()) : "server";
				data = PacketChunker.instance.receive(sender, raw);
				if (data == null) return;
			}
			ByteArrayDataInput in = ByteStreams.newDataInput(data);
			byte type = in.readByte();
			if (side == Side.SERVER) {
				if (player == null) return;
				handleServer(type, in, player);
			} else {
				handleClient(type, in);
			}
		} catch (Exception e) {
			CCLights2.logger.warn("Failed to handle CCLights2 packet on the " + side + ": " + e);
			if (ds.mods.CCLights2.Config.DEBUG) e.printStackTrace();
		}
	}

	// ------------------------------------------------------------------ server

	private static void handleServer(byte type, ByteArrayDataInput in, EntityPlayer player) throws IOException {
		World world = player.worldObj;
		switch (type) {
		case NET_GPUMOUSE: {
			TileEntityMonitor mtile = monitorAt(world, in);
			if (mtile == null || !mtile.canInteract(player)) return;
			int sub = in.readInt();
			Monitor mon = mtile.getMonitor();
			if (mon == null) return;
			switch (sub) {
			case 0: {
				int button = in.readInt(), mx = in.readInt(), my = in.readInt();
				for (GPU g : mon.gpus) if (g.tile != null) g.tile.startClick(player, button, mx, my);
				break;
			}
			case 1: {
				int mx = in.readInt(), my = in.readInt();
				for (GPU g : mon.gpus) if (g.tile != null) g.tile.moveClick(player, mx, my);
				break;
			}
			case 2:
				for (GPU g : mon.gpus) if (g.tile != null) g.tile.endClick(player);
				break;
			}
			break;
		}
		case NET_GPUEVENT: {
			TileEntityMonitor mtile = monitorAt(world, in);
			if (mtile == null || !mtile.canInteract(player)) return;
			String event = Serialize.readString(in);
			int n = in.readInt();
			if (n < 0 || n > 64) return;
			Object[] args = new Object[n];
			for (int i = 0; i < n; i++) args[i] = Serialize.read(in);
			Monitor mon = mtile.getMonitor();
			if (mon == null) return;
			for (GPU g : mon.gpus) {
				if (g.tile != null) g.tile.queueEvent(event, args);
			}
			break;
		}
		case NET_GPUDOWNLOAD: {
			int x = in.readInt(), y = in.readInt(), z = in.readInt();
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileEntityGPU) PacketSenders.sendGPUSync((TileEntityGPU) te, player);
			break;
		}
		case NET_SCREENSHOT: {
			int x = in.readInt(), y = in.readInt(), z = in.readInt();
			int len = in.readInt();
			if (len < 0 || len > 4 * 1024 * 1024) return;
			byte[] jpeg = new byte[len];
			in.readFully(jpeg);
			TileEntity te = world.getTileEntity(x, y, z);
			if (!(te instanceof TileEntityTTrans)) return;
			TileEntityTTrans tile = (TileEntityTTrans) te;
			if (!tile.canInteract(player)) return;
			// Lua sees the image as a table of byte values, ready for gpu.import().
			java.util.HashMap<Double, Double> table = new java.util.HashMap<Double, Double>(len * 2);
			for (int i = 0; i < len; i++) table.put((double) (i + 1), (double) (jpeg[i] & 0xFF));
			tile.queueEvent("tablet_image", new Object[] { table, player.getCommandSenderName() });
			break;
		}
		default:
			break;
		}
	}

	private static TileEntityMonitor monitorAt(World world, ByteArrayDataInput in) {
		int x = in.readInt(), y = in.readInt(), z = in.readInt();
		TileEntity te = world.getTileEntity(x, y, z);
		return te instanceof TileEntityMonitor ? (TileEntityMonitor) te : null;
	}

	// ------------------------------------------------------------------ client

	private static void handleClient(byte type, ByteArrayDataInput in) throws IOException {
		World world = CCLights2.proxy.getClientWorld();
		if (world == null) return;
		switch (type) {
		case NET_GPUDRAWLIST: {
			int x = in.readInt(), y = in.readInt(), z = in.readInt();
			TileEntity te = world.getTileEntity(x, y, z);
			int n = in.readInt();
			if (n < 0 || n > 1000000) return;
			List<DrawCMD> cmds = new ArrayList<DrawCMD>(n);
			for (int i = 0; i < n; i++) cmds.add(Serialize.readCommand(in));
			if (te instanceof TileEntityGPU) {
				CCLights2.proxy.submitDraw(((TileEntityGPU) te).gpu, cmds);
			}
			break;
		}
		case NET_GPUSYNC: {
			int x = in.readInt(), y = in.readInt(), z = in.readInt();
			TileEntity te = world.getTileEntity(x, y, z);
			if (!(te instanceof TileEntityGPU)) return;
			GPU gpu = ((TileEntityGPU) te).gpu;
			synchronized (gpu) {
				gpu.reset();
				gpu.state.read(in);
				int bound = in.readInt();
				int count = in.readInt();
				for (int i = 0; i < count; i++) {
					int id = in.readInt();
					int w = in.readInt(), h = in.readInt();
					int[] px = (int[]) Serialize.read(in);
					if (id < 0 || id >= GPU.MAX_TEXTURES) continue;
					if (id == 0) {
						// The screen: only if this client already has the monitor attached.
						Texture screen = gpu.textures[0];
						if (screen != null && screen.getWidth() == w && screen.getHeight() == h) {
							screen.setPixels(0, 0, w, h, px);
							screen.texUpdate();
						}
						continue;
					}
					Texture t = new Texture(w, h);
					t.setPixels(0, 0, w, h, px);
					gpu.textures[id] = t;
				}
				if (bound > 0 && bound < GPU.MAX_TEXTURES && gpu.textures[bound] != null) {
					gpu.bindedTexture = gpu.textures[bound];
					gpu.bindedSlot = bound;
				}
				int shaderCount = in.readInt();
				for (int i = 0; i < shaderCount; i++) {
					int id = in.readInt();
					String src = Serialize.readString(in);
					float[] values = (float[]) Serialize.read(in);
					int[] samplers = (int[]) Serialize.read(in);
					try {
						int slot = gpu.createShader(src, id);
						ShaderInstance sh = gpu.shaders[slot];
						if (values.length == sh.values.length) System.arraycopy(values, 0, sh.values, 0, values.length);
						System.arraycopy(samplers, 0, sh.samplers, 0, Math.min(samplers.length, sh.samplers.length));
					} catch (Exception e) {
						CCLights2.logger.warn("Could not restore shader " + id + " from sync: " + e.getMessage());
					}
				}
			}
			break;
		}
		default:
			break;
		}
	}

	/** Queues a ComputerCraft event on every computer attached to a GPU tile. */
	public static void queueEvent(TileEntityGPU tile, String event, Object[] args) {
		for (IComputerAccess c : tile.computers()) {
			if (c != null) c.queueEvent(event, args);
		}
	}
}
