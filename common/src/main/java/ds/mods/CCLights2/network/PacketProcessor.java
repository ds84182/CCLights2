package ds.mods.CCLights2.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.entity.GpuBlockEntity;
import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Dispatches received messages (port of the 1.7.10 PacketProcessor switch). Registered by
 * CCLights2.init() through Platform.registerNetworkReceivers; both methods run on the main thread.
 * Client-bound kinds are decoded in {@link ClientPacketHandlers}, which this class only touches from
 * {@link #handleClient}, so a dedicated server never loads client classes.
 */
public final class PacketProcessor {
	private PacketProcessor() {}

	/** Largest decompressed message the server accepts from a client. */
	public static final int SERVER_MAX_DECOMPRESSED = 8 * 1024 * 1024;
	/** Largest decompressed message a client accepts from the server (a full GPU sync can be big). */
	public static final int CLIENT_MAX_DECOMPRESSED = 64 * 1024 * 1024;

	/** Chunks from clients, keyed by player UUID. */
	private static final ChunkReassembler SERVER_CHUNKS = new ChunkReassembler(SERVER_MAX_DECOMPRESSED);
	/** Chunks from the server. */
	private static final ChunkReassembler CLIENT_CHUNKS = new ChunkReassembler(CLIENT_MAX_DECOMPRESSED);
	private static final Object FROM_SERVER = "server";

	private static final RateLimiter SYNC_LIMIT = new RateLimiter(500_000_000L);
	private static final RateLimiter SCREENSHOT_LIMIT = new RateLimiter(1_000_000_000L);

	/** A decoded logical message: kind plus a reader positioned at its payload. */
	private record Message(int kind, PayloadInput in) {}

	/** Resolves NET_SPLITPACKET; returns null while a split message is incomplete. */
	private static Message unwrap(int kind, byte[] payload, ChunkReassembler chunks, Object sender) throws IOException {
		if (kind != NetworkKinds.NET_SPLITPACKET) return new Message(kind, new PayloadInput(payload));
		byte[] frame = chunks.accept(sender, payload);
		if (frame == null) return null;
		if (frame.length < 1) throw new IOException("empty split message");
		int inner = frame[0];
		if (inner == NetworkKinds.NET_SPLITPACKET) throw new IOException("nested split message");
		return new Message(inner, new PayloadInput(frame, 1));
	}

	private static void warn(String side, int kind, Exception e) {
		CCLights2.LOGGER.warn("Dropped CCLights2 message " + kind + " on the " + side + ": " + e);
		if (Config.DEBUG) CCLights2.LOGGER.warn("", e);
	}

	// ------------------------------------------------------------------ server

	/** Server side: a message from {@code player}. */
	public static void handleServer(int kind, byte[] payload, @Nullable ServerPlayer player) {
		if (player == null) return;
		try {
			Message m = unwrap(kind, payload, SERVER_CHUNKS, player.getUUID());
			if (m == null) return;
			handleServerMessage(m.kind(), m.in(), player);
		} catch (Exception e) {
			warn("server (from " + player.getGameProfile().getName() + ")", kind, e);
		}
	}

	private static void handleServerMessage(int kind, PayloadInput in, ServerPlayer player) throws IOException {
		switch (kind) {
		case NetworkKinds.NET_GPUMOUSE: {
			MonitorBlockEntity mon = find(player, WireFormat.readTarget(in), MonitorBlockEntity.class);
			if (mon == null || !mon.canInteract(player)) return;
			int sub = in.readInt();
			List<GpuBlockEntity> gpus = gpusOf(mon);
			switch (sub) {
			case 0: {
				int button = in.readInt(), x = in.readInt(), y = in.readInt();
				for (GpuBlockEntity g : gpus) g.startClick(player, button, x, y);
				break;
			}
			case 1: {
				int x = in.readInt(), y = in.readInt();
				for (GpuBlockEntity g : gpus) g.moveClick(player, x, y);
				break;
			}
			case 2:
				for (GpuBlockEntity g : gpus) g.endClick(player);
				break;
			default:
				throw new IOException("unknown mouse action " + sub);
			}
			break;
		}
		case NetworkKinds.NET_GPUEVENT: {
			MonitorBlockEntity mon = find(player, WireFormat.readTarget(in), MonitorBlockEntity.class);
			if (mon == null || !mon.canInteract(player)) return;
			String event = Serialize.readString(in);
			int n = in.readInt();
			if (n < 0 || n > 8) throw new IOException("bad event argument count " + n);
			Object[] args = new Object[n];
			for (int i = 0; i < n; i++) args[i] = Serialize.read(in);
			if (!WireFormat.isAllowedClientEvent(event, args)) throw new IOException("event '" + event + "' is not allowed from clients");
			mon.queueEvent(event, args);
			break;
		}
		case NetworkKinds.NET_GPUSYNC: {
			WireFormat.Target t = WireFormat.readTarget(in);
			GpuBlockEntity gpu = find(player, t, GpuBlockEntity.class);
			if (gpu == null || !isTracking(player, gpu)) return;
			if (!SYNC_LIMIT.tryAcquire(player.getUUID() + "@" + t.pos(), System.nanoTime())) return;
			// Answered from the GPU's next server tick, right after its pending draw commands are flushed,
			// so the snapshot never contains commands the client will also receive as a draw list.
			gpu.queueSync(player);
			break;
		}
		case NetworkKinds.NET_SCREENSHOT: {
			TabletTransceiverBlockEntity tile = find(player, WireFormat.readTarget(in), TabletTransceiverBlockEntity.class);
			byte[] png = WireFormat.readBytes(in, WireFormat.MAX_SCREENSHOT);
			if (tile == null || !tile.canInteract(player)) return;
			if (!SCREENSHOT_LIMIT.tryAcquire(player.getUUID(), System.nanoTime())) return;
			// Lua sees the image as a table of byte values, ready for gpu.import().
			Map<Double, Double> table = WireFormat.byteTable(png);
			tile.queueEvent("tablet_image", new Object[] { table, player.getGameProfile().getName() });
			break;
		}
		default:
			throw new IOException("unexpected message kind " + kind + " from a client");
		}
	}

	/**
	 * The block entity at a target in the player's own level, if its chunk is loaded and it has the expected
	 * class. Never loads chunks on behalf of a client.
	 */
	private static <T extends BlockEntity> T find(ServerPlayer player, WireFormat.Target t, Class<T> type) {
		Level level = player.level();
		if (!level.dimension().location().toString().equals(t.dimension())) return null;
		BlockPos pos = BlockPos.of(t.pos());
		if (!level.isLoaded(pos)) return null;
		BlockEntity be = level.getBlockEntity(pos);
		return type.isInstance(be) ? type.cast(be) : null;
	}

	/** Only players that receive a GPU's draw lists may ask for its full state. */
	private static boolean isTracking(ServerPlayer player, BlockEntity be) {
		if (!(be.getLevel() instanceof ServerLevel level)) return false;
		ServerChunkCache cache = level.getChunkSource();
		return cache.chunkMap.getPlayers(new ChunkPos(be.getBlockPos()), false).contains(player);
	}

	/** The GPU block entities driving a monitor (copied, so callbacks may change the list). */
	private static List<GpuBlockEntity> gpusOf(MonitorBlockEntity mon) {
		List<GpuBlockEntity> out = new ArrayList<>();
		Monitor m = mon.getMonitor();
		if (m == null) return out;
		for (GPU g : new ArrayList<>(m.gpus)) {
			if (g != null && g.tile instanceof GpuBlockEntity be) out.add(be);
		}
		return out;
	}

	// ------------------------------------------------------------------ client

	/** Client side: a message from the server ({@code player} is always null). */
	public static void handleClient(int kind, byte[] payload, @Nullable ServerPlayer player) {
		try {
			Message m = unwrap(kind, payload, CLIENT_CHUNKS, FROM_SERVER);
			if (m == null) return;
			ClientPacketHandlers.handle(m.kind(), m.in());
		} catch (Exception e) {
			warn("client", kind, e);
		}
	}

	/** Drops partial messages from the server, e.g. when leaving a world. Client side. */
	public static void clearClientState() {
		CLIENT_CHUNKS.clear();
		PendingSyncs.clear();
	}
}
