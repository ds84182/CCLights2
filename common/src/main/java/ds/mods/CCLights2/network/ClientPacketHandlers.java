package ds.mods.CCLights2.network;

import java.io.IOException;
import java.util.List;

import ds.mods.CCLights2.block.entity.GpuBlockEntity;
import ds.mods.CCLights2.client.ClientDrawThread;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client-bound message handlers. Client only: references {@link Minecraft}, and is reached solely from
 * {@link PacketProcessor#handleClient}, which is only installed on the physical client.
 * Also owns the {@link ClientDrawThread} that replays draw lists.
 */
public final class ClientPacketHandlers {
	private ClientPacketHandlers() {}

	private static ClientDrawThread drawThread;

	/** The draw thread, started on first use. */
	public static synchronized ClientDrawThread drawThread() {
		if (drawThread == null || !drawThread.isAlive()) {
			drawThread = new ClientDrawThread();
			drawThread.start();
		}
		return drawThread;
	}

	/** Forgets queued draw work and partial messages, e.g. when the client leaves a world. */
	public static synchronized void clear() {
		if (drawThread != null) drawThread.clear();
		PacketProcessor.clearClientState();
	}

	static void handle(int kind, PayloadInput in) throws IOException {
		switch (kind) {
		case NetworkKinds.NET_GPUDRAWLIST: {
			WireFormat.Target t = WireFormat.readTarget(in);
			List<DrawCMD> cmds = WireFormat.readDrawList(in);
			GpuBlockEntity be = find(t);
			GPU gpu = be == null ? null : be.getGpu();
			if (gpu == null || cmds.isEmpty()) return;
			// Contained in the snapshot we are waiting for; replaying would apply them twice.
			if (PendingSyncs.isAwaiting(gpu, System.nanoTime())) return;
			drawThread().submit(gpu, cmds);
			break;
		}
		case NetworkKinds.NET_GPUSYNC: {
			WireFormat.Target t = WireFormat.readTarget(in);
			WireFormat.GpuSnapshot snap = WireFormat.readGpuState(in);
			GpuBlockEntity be = find(t);
			GPU gpu = be == null ? null : be.getGpu();
			if (gpu == null) return;
			PendingSyncs.received(gpu);
			WireFormat.applyGpuState(gpu, snap);
			break;
		}
		default:
			throw new IOException("unexpected message kind " + kind + " from the server");
		}
	}

	private static GpuBlockEntity find(WireFormat.Target t) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !level.dimension().location().toString().equals(t.dimension())) return null;
		BlockPos pos = BlockPos.of(t.pos());
		if (!level.isLoaded(pos)) return null;
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof GpuBlockEntity g ? g : null;
	}
}
