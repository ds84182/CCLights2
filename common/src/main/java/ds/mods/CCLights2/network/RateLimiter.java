package ds.mods.CCLights2.network;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows one action per key per interval (e.g. one GPU sync per player and GPU per half second).
 * The map is pruned of expired keys once it grows past {@link #PRUNE_AT}. Minecraft-free.
 */
public final class RateLimiter {
	private static final int PRUNE_AT = 1024;

	private final long intervalNanos;
	private final Map<Object, Long> last = new HashMap<>();

	public RateLimiter(long intervalNanos) {
		this.intervalNanos = intervalNanos;
	}

	/** True (and records the time) when {@code key} has not acted within the interval before {@code now}. */
	public synchronized boolean tryAcquire(Object key, long now) {
		Long prev = last.get(key);
		if (prev != null && now - prev < intervalNanos) return false;
		if (last.size() >= PRUNE_AT) last.values().removeIf(t -> now - t >= intervalNanos);
		last.put(key, now);
		return true;
	}
}
