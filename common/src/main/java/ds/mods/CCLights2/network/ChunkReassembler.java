package ds.mods.CCLights2.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.zip.GZIPInputStream;

/**
 * Receiving side of {@link PacketChunker}: collects {@code NET_SPLITPACKET} chunks per sender and returns the
 * decompressed frame ({@code [kind byte] + payload}) once a message is complete. Minecraft-free.
 *
 * <p>Bounded: per sender at most {@link #maxPending} unfinished messages and {@link #maxAssembled} buffered
 * compressed bytes (a message that would exceed it is dropped, and its later chunks are ignored);
 * decompression stops at {@link #maxDecompressed}; unfinished messages expire after {@link #timeoutNanos}
 * (which also cleans up after senders that disconnect mid-message).
 */
public final class ChunkReassembler {
	/** Default compressed cap per message and per sender. */
	public static final int DEFAULT_MAX_ASSEMBLED = PacketChunker.MAX_MESSAGE;
	/** Default expiry of unfinished messages. */
	public static final long DEFAULT_TIMEOUT_NANOS = 30_000_000_000L;

	private final int maxAssembled;
	private final int maxDecompressed;
	private final int maxPending;
	private final long timeoutNanos;
	private final LongSupplier clock;

	private final Map<Object, SenderState> senders = new HashMap<>();

	private static final class Partial {
		final byte[][] parts;
		int received;
		int bytes;
		final long started;

		Partial(int count, long now) {
			parts = new byte[count][];
			started = now;
		}
	}

	private static final class SenderState {
		/** Insertion ordered, oldest first. */
		final LinkedHashMap<Integer, Partial> pending = new LinkedHashMap<>();
		/** Ids dropped for being too large; their remaining chunks are ignored. */
		final Deque<Integer> dropped = new ArrayDeque<>();
		int bytes;
		long lastSeen;
	}

	public ChunkReassembler(int maxDecompressed) {
		this(DEFAULT_MAX_ASSEMBLED, maxDecompressed, 8, DEFAULT_TIMEOUT_NANOS, System::nanoTime);
	}

	public ChunkReassembler(int maxAssembled, int maxDecompressed, int maxPending, long timeoutNanos, LongSupplier clock) {
		this.maxAssembled = maxAssembled;
		this.maxDecompressed = maxDecompressed;
		this.maxPending = maxPending;
		this.timeoutNanos = timeoutNanos;
		this.clock = clock;
	}

	/**
	 * Feeds one chunk (the whole NET_SPLITPACKET payload, header included).
	 *
	 * @param sender any key unique per peer (player UUID on the server, a constant on the client)
	 * @return the decompressed frame when this chunk completed a message, else null
	 * @throws IOException for malformed chunks, oversized or corrupt messages (the message is discarded)
	 */
	public synchronized byte[] accept(Object sender, byte[] chunk) throws IOException {
		long now = clock.getAsLong();
		expire(now);
		if (chunk.length < PacketChunker.HEADER_SIZE || chunk[0] != NetworkKinds.NET_SPLITPACKET) throw new IOException("truncated chunk");
		int count = ((chunk[1] & 0xFF) << 8) | (chunk[2] & 0xFF);
		int index = ((chunk[3] & 0xFF) << 8) | (chunk[4] & 0xFF);
		int id = ((chunk[5] & 0xFF) << 8) | (chunk[6] & 0xFF);
		if (count <= 0 || index >= count) throw new IOException("bad chunk header " + index + "/" + count);
		int size = chunk.length - PacketChunker.HEADER_SIZE;

		SenderState s = senders.computeIfAbsent(sender, k -> new SenderState());
		s.lastSeen = now;
		if (s.dropped.contains(id)) {
			if (index == count - 1) s.dropped.remove(id);
			return null;
		}

		Partial p = s.pending.get(id);
		if (p != null && p.parts.length != count) {
			// Id reused for a different message (wrap-around or a lost tail): start over.
			discard(s, id);
			p = null;
		}
		if (p == null) {
			while (s.pending.size() >= maxPending) discard(s, s.pending.keySet().iterator().next());
			p = new Partial(count, now);
			s.pending.put(id, p);
		}
		if (p.parts[index] != null) throw new IOException("duplicate chunk " + index + " of message " + id);

		if (p.bytes + (long) size > maxAssembled) {
			discard(s, id);
			markDropped(s, id);
			throw new IOException("message " + id + " exceeds " + maxAssembled + " bytes; dropped");
		}
		// Make room within the per-sender budget by dropping older unfinished messages.
		while (s.bytes + size > maxAssembled) {
			Integer victim = null;
			for (Integer k : s.pending.keySet()) {
				if (k != id) {
					victim = k;
					break;
				}
			}
			if (victim == null) break;
			discard(s, victim);
		}

		byte[] part = new byte[size];
		System.arraycopy(chunk, PacketChunker.HEADER_SIZE, part, 0, size);
		p.parts[index] = part;
		p.received++;
		p.bytes += size;
		s.bytes += size;
		if (p.received < count) return null;

		discard(s, id);
		if (s.pending.isEmpty() && s.dropped.isEmpty()) senders.remove(sender);
		byte[] full = new byte[p.bytes];
		int off = 0;
		for (byte[] b : p.parts) {
			System.arraycopy(b, 0, full, off, b.length);
			off += b.length;
		}
		return gunzip(full, maxDecompressed);
	}

	/** Forgets unfinished messages from one sender. */
	public synchronized void forget(Object sender) {
		senders.remove(sender);
	}

	/** Forgets everything. */
	public synchronized void clear() {
		senders.clear();
	}

	/** Buffered compressed bytes across all senders (for tests and diagnostics). */
	public synchronized int bufferedBytes() {
		int n = 0;
		for (SenderState s : senders.values()) n += s.bytes;
		return n;
	}

	/** Unfinished messages across all senders (for tests and diagnostics). */
	public synchronized int pendingMessages() {
		int n = 0;
		for (SenderState s : senders.values()) n += s.pending.size();
		return n;
	}

	private void discard(SenderState s, int id) {
		Partial p = s.pending.remove(id);
		if (p != null) s.bytes -= p.bytes;
	}

	private static void markDropped(SenderState s, int id) {
		s.dropped.addLast(id);
		while (s.dropped.size() > 16) s.dropped.removeFirst();
	}

	private void expire(long now) {
		Iterator<SenderState> it = senders.values().iterator();
		while (it.hasNext()) {
			SenderState s = it.next();
			Iterator<Map.Entry<Integer, Partial>> pit = s.pending.entrySet().iterator();
			while (pit.hasNext()) {
				Partial p = pit.next().getValue();
				if (now - p.started > timeoutNanos) {
					s.bytes -= p.bytes;
					pit.remove();
				}
			}
			if (s.pending.isEmpty() && now - s.lastSeen > timeoutNanos) it.remove();
		}
	}

	/** Decompresses a GZIP stream, failing once the output would exceed {@code limit} bytes. */
	public static byte[] gunzip(byte[] data, int limit) throws IOException {
		try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(data))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, Math.max(64, data.length * 2)));
			byte[] buf = new byte[8192];
			int n;
			while ((n = zip.read(buf)) > 0) {
				if (out.size() + n > limit) throw new IOException("message decompresses to more than " + limit + " bytes; dropped");
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		}
	}
}
