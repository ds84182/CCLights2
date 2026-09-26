package ds.mods.CCLights2.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ds.mods.CCLights2.network.PacketHandler.PacketMessage;

/**
 * Compresses a payload and splits it into packets small enough for the Forge channel.
 * Reassembly is keyed by sender so several clients can talk to the server at once.
 */
public class PacketChunker {
	/** Below this size a payload is sent as a single uncompressed packet. */
	public static final int INLINE_LIMIT = 1024;
	public static final int CHUNK_SIZE = 30000;

	private int packetId = 0;
	private final Map<String, byte[][]> pending = new HashMap<String, byte[][]>();

	public static final PacketChunker instance = new PacketChunker();

	public synchronized PacketMessage[] createPackets(byte[] input) throws IOException {
		if (input.length <= INLINE_LIMIT) {
			return new PacketMessage[] { new PacketMessage(input) };
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length / 2 + 64);
		GZIPOutputStream zip = new GZIPOutputStream(bos);
		zip.write(input);
		zip.close();
		byte[] data = bos.toByteArray();

		int numChunks = (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
		if (numChunks > Short.MAX_VALUE) throw new IOException("payload too large: " + input.length + " bytes");
		int id = packetId++;
		PacketMessage[] packets = new PacketMessage[numChunks];
		int start = 0;
		for (int i = 0; i < numChunks; i++) {
			int size = Math.min(data.length - start, CHUNK_SIZE);
			byte[] chunk = new byte[7 + size];
			chunk[0] = PacketProcessor.NET_SPLITPACKET;
			chunk[1] = (byte) (numChunks >> 8);
			chunk[2] = (byte) numChunks;
			chunk[3] = (byte) (i >> 8);
			chunk[4] = (byte) i;
			chunk[5] = (byte) (id >> 8);
			chunk[6] = (byte) id;
			System.arraycopy(data, start, chunk, 7, size);
			packets[i] = new PacketMessage(chunk);
			start += size;
		}
		return packets;
	}

	/**
	 * Feeds one chunk; returns the decompressed payload once every chunk of that message arrived.
	 * @param sender something unique per peer, e.g. the player name
	 */
	public synchronized byte[] receive(String sender, byte[] chunk) throws IOException {
		if (chunk.length < 7) throw new IOException("truncated chunk");
		int numChunks = ((chunk[1] & 0xFF) << 8) | (chunk[2] & 0xFF);
		int index = ((chunk[3] & 0xFF) << 8) | (chunk[4] & 0xFF);
		int id = ((chunk[5] & 0xFF) << 8) | (chunk[6] & 0xFF);
		if (numChunks <= 0 || index < 0 || index >= numChunks) throw new IOException("bad chunk header");
		String key = sender + "#" + id;
		byte[][] parts = pending.get(key);
		if (parts == null || parts.length != numChunks) {
			parts = new byte[numChunks][];
			pending.put(key, parts);
		}
		byte[] part = new byte[chunk.length - 7];
		System.arraycopy(chunk, 7, part, 0, part.length);
		parts[index] = part;

		int total = 0;
		for (byte[] p : parts) {
			if (p == null) return null;
			total += p.length;
		}
		pending.remove(key);
		byte[] full = new byte[total];
		int off = 0;
		for (byte[] p : parts) {
			System.arraycopy(p, 0, full, off, p.length);
			off += p.length;
		}
		GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(full));
		ByteArrayOutputStream out = new ByteArrayOutputStream(total * 2);
		byte[] buf = new byte[8192];
		int n;
		while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
		zip.close();
		return out.toByteArray();
	}

	/** Forgets partial messages from a peer, e.g. when a player disconnects. */
	public synchronized void forget(String sender) {
		pending.keySet().removeIf(k -> k.startsWith(sender + "#"));
	}
}
