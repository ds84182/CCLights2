package ds.mods.CCLights2.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Client-side registry of where each tablet transceiver is, keyed by the transceiver's UUID.
 * Tablets only store the UUID, so this is how the tablet finds its screen. Filled by
 * TabletTransceiverBlockEntity when it is loaded on the client.
 */
public final class TabMesg {
	private static final Map<UUID, BlockPos> positions = new ConcurrentHashMap<>();

	private TabMesg() {}

	public static void setPosition(UUID transceiver, BlockPos pos) {
		positions.put(transceiver, pos.immutable());
	}

	/** Position of the transceiver, or null if this client has not seen it yet. */
	@Nullable
	public static BlockPos getPosition(UUID transceiver) {
		return positions.get(transceiver);
	}

	/** Forgets a transceiver, but only if it is still registered at {@code pos}. */
	public static void remove(UUID transceiver, BlockPos pos) {
		positions.remove(transceiver, pos);
	}

	/** Called when the client leaves a world. */
	public static void clear() {
		positions.clear();
	}
}
