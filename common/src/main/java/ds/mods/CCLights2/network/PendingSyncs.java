package ds.mods.CCLights2.network;

import java.util.Map;
import java.util.WeakHashMap;

import ds.mods.CCLights2.gpu.GPU;

/**
 * Client side: GPUs that asked the server for a NET_GPUSYNC and have not received it yet. Draw lists
 * arriving in between are already contained in the coming snapshot (the server answers the request after
 * sending them), so they are dropped instead of being replayed twice. A request that gets no answer
 * expires after {@link #TIMEOUT_NANOS}. Minecraft-free.
 */
public final class PendingSyncs {
	public static final long TIMEOUT_NANOS = 5_000_000_000L;

	private static final Map<GPU, Long> deadlines = new WeakHashMap<>();

	private PendingSyncs() {}

	public static synchronized void requested(GPU gpu, long now) {
		deadlines.put(gpu, now + TIMEOUT_NANOS);
	}

	public static final int NONE = 0, AWAITING = 1, EXPIRED = 2;

	/**
	 * NONE when no sync is outstanding, AWAITING while one is, EXPIRED (once) when the request timed
	 * out: draw lists were dropped meanwhile, so the caller must ask for a fresh sync.
	 */
	public static synchronized int state(GPU gpu, long now) {
		Long d = deadlines.get(gpu);
		if (d == null) return NONE;
		if (now - d > 0) {
			deadlines.remove(gpu);
			return EXPIRED;
		}
		return AWAITING;
	}

	public static synchronized boolean isAwaiting(GPU gpu, long now) {
		Long d = deadlines.get(gpu);
		if (d == null) return false;
		if (now - d > 0) {
			deadlines.remove(gpu);
			return false;
		}
		return true;
	}

	public static synchronized void received(GPU gpu) {
		deadlines.remove(gpu);
	}

	public static synchronized void clear() {
		deadlines.clear();
	}
}
