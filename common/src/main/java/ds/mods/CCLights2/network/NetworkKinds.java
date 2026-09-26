package ds.mods.CCLights2.network;

import ds.mods.CCLights2.CCLights2;
import net.minecraft.resources.ResourceLocation;

/**
 * Message kinds on the single {@code cclights:msg} channel (transport message = VarInt kind + byte[] payload).
 * Values match the 1.7.10 NET_* bytes. Every payload starts with a target: {@code long BlockPos.asLong(),
 * int length + UTF-8 dimension id} (see {@link WireFormat#writeTarget}). Ints are big endian; "value" means
 * a {@link Serialize} typed value.
 */
public final class NetworkKinds {
	/** The one custom payload channel both loaders use. */
	public static final ResourceLocation CHANNEL = new ResourceLocation(CCLights2.MOD_ID, "msg");

	/** Server to client: target GPU, {@code int n}, n x ({@code int CommandEnum ordinal, int argc, argc x value}). */
	public static final int NET_GPUDRAWLIST = 0;
	/**
	 * Client to server: target monitor, {@code string event, int argc, argc x value}. Only
	 * monitor_scroll(x,y,dir), key(code,repeat), key_up(code) and char(string) are accepted.
	 */
	public static final int NET_GPUEVENT = 1;
	/**
	 * Unused since the 1.20.1 port. In 1.7.10 it was the client's sync request; that request is now
	 * NET_GPUSYNC sent client to server. The number stays reserved.
	 */
	@Deprecated
	public static final int NET_GPUDOWNLOAD = 2;
	/**
	 * Client to server: target monitor, {@code int sub}; sub 0 (down) {@code int button, int x, int y},
	 * 1 (move) {@code int x, int y}, 2 (up) nothing.
	 */
	public static final int NET_GPUMOUSE = 3;
	/**
	 * Client to server: target GPU (a request for its state). Server to client: target GPU followed by
	 * {@link WireFormat#writeGpuState} (draw state, every texture incl. slot 0 as raw ARGB, shaders).
	 */
	public static final int NET_GPUSYNC = 4;
	/**
	 * Either direction: one piece of a compressed message ({@link PacketChunker}); the reassembled,
	 * decompressed frame is {@code [inner kind byte] + inner payload}.
	 */
	public static final int NET_SPLITPACKET = 8;
	/** Client to server: target tablet transceiver, {@code int length, PNG bytes} (at most 2 MB). */
	public static final int NET_SCREENSHOT = 10;

	private NetworkKinds() {}
}
