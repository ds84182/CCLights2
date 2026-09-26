package ds.mods.CCLights2.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side registry of where each tablet transceiver is, keyed by the transceiver's UUID.
 * Tablets only store the UUID, so this is how the item finds its screen.
 */
public final class TabMesg {
	private TabMesg() {}

	private static final Map<UUID, int[]> positions = new ConcurrentHashMap<UUID, int[]>();

	public static void setPosition(UUID transceiver, int x, int y, int z) {
		positions.put(transceiver, new int[] { x, y, z });
	}

	/** {x, y, z} of the transceiver, or null if this client has not seen it yet. */
	public static int[] getPosition(UUID transceiver) {
		return positions.get(transceiver);
	}

	public static void clear() {
		positions.clear();
	}
}
