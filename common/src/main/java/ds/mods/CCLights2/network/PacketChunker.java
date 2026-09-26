package ds.mods.CCLights2.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;


/**
 * Compresses a payload and splits it into packets small enough for a custom payload packet.
 * Each chunk is {@code [NET_SPLITPACKET, count hi, count lo, index hi, index lo, id hi, id lo]} followed
 * by up to {@link #CHUNK_SIZE} bytes of the GZIP stream. Small payloads pass through unchanged.
 * The receiving side is {@link ChunkReassembler}. Minecraft-free.
 *
 * <p>Size budget: serverbound custom payloads are capped at 32767 bytes of data. The loader transport adds
 * at most 1 byte (Forge discriminator) + 5 (VarInt kind) + 5 (VarInt array length), so a chunk of
 * 7 + 30000 bytes stays well inside it (and far inside the 1 MB clientbound cap).
 */
public class PacketChunker {
	/** Below this size a payload is sent as a single uncompressed packet. */
	public static final int INLINE_LIMIT = 1024;
	/** GZIP bytes per chunk. */
	public static final int CHUNK_SIZE = 30000;
	/** Chunk header: kind byte, chunk count, chunk index, message id (shorts, big endian). */
	public static final int HEADER_SIZE = 7;
	/** Largest compressed message a receiver accepts; senders refuse anything bigger. */
	public static final int MAX_MESSAGE = 8 * 1024 * 1024;

	private int packetId = 0;

	public static final PacketChunker instance = new PacketChunker();

	/** One transport message: the kind and the payload bytes. */
	public record Packet(int kind, byte[] payload) {}

	/**
	 * Splits {@code input}. Returns {@code {input}} itself for payloads up to {@link #INLINE_LIMIT} bytes,
	 * otherwise GZIP chunks that each start with the {@link NetworkKinds#NET_SPLITPACKET} header.
	 */
	public synchronized byte[][] createPackets(byte[] input) throws IOException {
		if (input.length <= INLINE_LIMIT) {
			return new byte[][] { input };
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length / 2 + 64);
		GZIPOutputStream zip = new GZIPOutputStream(bos);
		zip.write(input);
		zip.close();
		byte[] data = bos.toByteArray();
		if (data.length > MAX_MESSAGE) {
			throw new IOException("message too large: " + data.length + " compressed bytes (limit " + MAX_MESSAGE + ")");
		}

		int numChunks = (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
		int id = packetId++ & 0xFFFF;
		byte[][] packets = new byte[numChunks][];
		int start = 0;
		for (int i = 0; i < numChunks; i++) {
			int size = Math.min(data.length - start, CHUNK_SIZE);
			byte[] chunk = new byte[HEADER_SIZE + size];
			chunk[0] = (byte) NetworkKinds.NET_SPLITPACKET;
			chunk[1] = (byte) (numChunks >> 8);
			chunk[2] = (byte) numChunks;
			chunk[3] = (byte) (i >> 8);
			chunk[4] = (byte) i;
			chunk[5] = (byte) (id >> 8);
			chunk[6] = (byte) id;
			System.arraycopy(data, start, chunk, HEADER_SIZE, size);
			packets[i] = chunk;
			start += size;
		}
		return packets;
	}

	/**
	 * Turns one logical message into transport messages. A small message goes out as {@code (kind, payload)};
	 * a big one is framed as {@code [kind byte] + payload}, compressed and sent as {@code (NET_SPLITPACKET, chunk)}
	 * messages, which {@link ChunkReassembler} turns back into the frame.
	 */
	public Packet[] split(int kind, byte[] payload) throws IOException {
		if (kind < 0 || kind > 127 || kind == NetworkKinds.NET_SPLITPACKET) throw new IllegalArgumentException("bad kind " + kind);
		if (payload.length + 1 <= INLINE_LIMIT) {
			return new Packet[] { new Packet(kind, payload) };
		}
		byte[] framed = new byte[payload.length + 1];
		framed[0] = (byte) kind;
		System.arraycopy(payload, 0, framed, 1, payload.length);
		byte[][] chunks = createPackets(framed);
		Packet[] out = new Packet[chunks.length];
		for (int i = 0; i < chunks.length; i++) out[i] = new Packet(NetworkKinds.NET_SPLITPACKET, chunks[i]);
		return out;
	}
}
