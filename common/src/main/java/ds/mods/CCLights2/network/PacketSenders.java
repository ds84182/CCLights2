package ds.mods.CCLights2.network;

import java.io.IOException;
import java.util.List;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.entity.GpuBlockEntity;
import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Builds and sends the NET_* messages (port of the 1.7.10 PacketSenders) through Services.PLATFORM,
 * split with {@link PacketChunker#split}. Wire formats are documented on {@link NetworkKinds}.
 * Uses no client-only classes, so it is safe to load on a dedicated server.
 */
public final class PacketSenders {
	private PacketSenders() {}

	private static ByteArrayDataOutput header(BlockEntity be) {
		Level level = be.getLevel();
		if (level == null) return null;
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		WireFormat.writeTarget(out, be.getBlockPos().asLong(), level.dimension().location().toString());
		return out;
	}

	private static PacketChunker.Packet[] split(int kind, byte[] payload) {
		try {
			return PacketChunker.instance.split(kind, payload);
		} catch (IOException e) {
			CCLights2.LOGGER.warn("Not sending CCLights2 message " + kind + ": " + e.getMessage());
			return new PacketChunker.Packet[0];
		}
	}

	private static void toServer(int kind, ByteArrayDataOutput out) {
		if (out == null) return;
		for (PacketChunker.Packet p : split(kind, out.toByteArray())) Services.PLATFORM.sendToServer(p.kind(), p.payload());
	}

	private static void toPlayer(int kind, byte[] payload, ServerPlayer player) {
		for (PacketChunker.Packet p : split(kind, payload)) Services.PLATFORM.sendToPlayer(player, p.kind(), p.payload());
	}

	private static void toTracking(int kind, byte[] payload, BlockEntity be) {
		for (PacketChunker.Packet p : split(kind, payload)) Services.PLATFORM.sendToTracking(be, p.kind(), p.payload());
	}

	// ------------------------------------------------------------------ server -> client

	/** Sends a GPU's flushed draw commands to every client tracking it (several messages if large). */
	public static void sendDrawList(GpuBlockEntity be, List<DrawCMD> cmds) {
		Level level = be.getLevel();
		if (level == null || level.isClientSide || cmds == null || cmds.isEmpty()) return;
		String dim = level.dimension().location().toString();
		for (byte[] payload : WireFormat.encodeDrawLists(be.getBlockPos().asLong(), dim, cmds, WireFormat.DRAWLIST_BATCH_BYTES)) {
			toTracking(NetworkKinds.NET_GPUDRAWLIST, payload, be);
		}
	}

	/**
	 * Sends the complete GPU state to one player (the answer to {@link #requestGpuSync}). Call it only when
	 * the GPU's draw list has just been flushed (GpuBlockEntity does this from its tick via queueSync).
	 */
	public static void sendGpuSync(GpuBlockEntity be, ServerPlayer target) {
		GPU gpu = be.getGpu();
		ByteArrayDataOutput out = header(be);
		if (gpu == null || out == null || target == null) return;
		WireFormat.writeGpuState(out, gpu);
		toPlayer(NetworkKinds.NET_GPUSYNC, out.toByteArray(), target);
	}

	// ------------------------------------------------------------------ client -> server

	/** Asks the server for a NET_GPUSYNC of this GPU; draw lists received until it arrives are skipped. */
	public static void requestGpuSync(GpuBlockEntity be) {
		ByteArrayDataOutput out = header(be);
		if (out == null) return;
		GPU gpu = be.getGpu();
		if (gpu != null) PendingSyncs.requested(gpu, System.nanoTime());
		toServer(NetworkKinds.NET_GPUSYNC, out);
	}

	public static void mouseDown(int x, int y, int button, MonitorBlockEntity target) {
		ByteArrayDataOutput out = header(target);
		if (out == null) return;
		out.writeInt(0);
		out.writeInt(button);
		out.writeInt(x);
		out.writeInt(y);
		toServer(NetworkKinds.NET_GPUMOUSE, out);
	}

	public static void mouseMove(int x, int y, MonitorBlockEntity target) {
		ByteArrayDataOutput out = header(target);
		if (out == null) return;
		out.writeInt(1);
		out.writeInt(x);
		out.writeInt(y);
		toServer(NetworkKinds.NET_GPUMOUSE, out);
	}

	public static void mouseUp(MonitorBlockEntity target) {
		ByteArrayDataOutput out = header(target);
		if (out == null) return;
		out.writeInt(2);
		toServer(NetworkKinds.NET_GPUMOUSE, out);
	}

	/** Raises a ComputerCraft event on the computers behind a monitor (see WireFormat.isAllowedClientEvent). */
	private static void event(MonitorBlockEntity target, String event, Object... args) {
		ByteArrayDataOutput out = header(target);
		if (out == null) return;
		Serialize.writeString(out, event);
		out.writeInt(args.length);
		for (Object a : args) Serialize.write(out, a);
		toServer(NetworkKinds.NET_GPUEVENT, out);
	}

	public static void scroll(int x, int y, int direction, MonitorBlockEntity target) {
		event(target, "monitor_scroll", x, y, direction);
	}

	/** {@code key} is a GLFW key code, which is what CC: Tweaked's keys API uses. */
	public static void keyDown(int key, boolean repeat, MonitorBlockEntity target) {
		event(target, "key", key, repeat);
	}

	public static void keyUp(int key, MonitorBlockEntity target) {
		event(target, "key_up", key);
	}

	public static void charTyped(char c, MonitorBlockEntity target) {
		event(target, "char", String.valueOf(c));
	}

	/** Ships an (already downscaled) PNG to a tablet transceiver; the server raises {@code tablet_image}. */
	public static void sendScreenshot(TabletTransceiverBlockEntity target, byte[] png) {
		if (png == null) return;
		if (png.length > WireFormat.MAX_SCREENSHOT) {
			CCLights2.LOGGER.warn("Tablet screenshot is " + png.length + " bytes; the limit is " + WireFormat.MAX_SCREENSHOT + ". Not sent.");
			return;
		}
		ByteArrayDataOutput out = header(target);
		if (out == null) return;
		out.writeInt(png.length);
		out.write(png);
		toServer(NetworkKinds.NET_SCREENSHOT, out);
	}
}
