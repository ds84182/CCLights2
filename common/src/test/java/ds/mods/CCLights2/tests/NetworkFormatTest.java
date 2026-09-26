package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.network.ChunkReassembler;
import ds.mods.CCLights2.network.NetworkKinds;
import ds.mods.CCLights2.network.PacketChunker;
import ds.mods.CCLights2.network.PayloadInput;
import ds.mods.CCLights2.network.Serialize;
import ds.mods.CCLights2.network.WireFormat;

/** Wire formats without Minecraft: Serialize values, chunking and reassembly, draw lists, GPU sync. */
class NetworkFormatTest {
	/** Serverbound custom payload data cap, and the transport's worst-case overhead around our payload. */
	private static final int SERVERBOUND_CAP = 32767;
	private static final int TRANSPORT_OVERHEAD = 1 + 5 + 5;

	private static Object roundTrip(Object v) throws IOException {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		Serialize.write(out, v);
		PayloadInput in = new PayloadInput(out.toByteArray());
		Object r = Serialize.read(in);
		assertEquals(0, in.remaining(), "trailing bytes after " + v);
		return r;
	}

	@Test
	void serializeEveryType() throws IOException {
		assertNull(roundTrip(null));
		assertEquals(42, roundTrip(42));
		assertEquals(-7, roundTrip((short) -7));
		assertEquals(5, roundTrip((byte) 5));
		assertEquals(Math.PI, roundTrip(Math.PI));
		assertEquals(Double.NaN, roundTrip(Double.NaN));
		assertEquals(1.5f, roundTrip(1.5f));
		assertEquals(Long.MIN_VALUE, roundTrip(Long.MIN_VALUE));
		assertEquals("", roundTrip(""));
		assertEquals("héllo wörld ⌂ 😀", roundTrip("héllo wörld ⌂ 😀"));
		char[] big = new char[70000];
		Arrays.fill(big, 'x');
		assertEquals(new String(big), roundTrip(new String(big)), "strings beyond writeUTF's 64K limit");
		assertEquals(true, roundTrip(true));
		assertEquals(false, roundTrip(false));
		assertEquals('Q', roundTrip('Q'));
		assertArrayEquals(new int[] { 1, -2, Integer.MAX_VALUE }, (int[]) roundTrip(new int[] { 1, -2, Integer.MAX_VALUE }));
		assertArrayEquals(new int[0], (int[]) roundTrip(new int[0]));
		assertArrayEquals(new float[] { 0.25f, -3f }, (float[]) roundTrip(new float[] { 0.25f, -3f }));
		assertArrayEquals(new byte[] { 0, -1, 127 }, (byte[]) roundTrip(new byte[] { 0, -1, 127 }));
		Object[] nested = { 1, null, "a", new Object[] { 2.0, new int[] { 3 }, new Object[0] } };
		Object[] back = (Object[]) roundTrip(nested);
		assertTrue(Arrays.deepEquals(nested, back), "nested object arrays with nulls");
		assertThrows(IllegalArgumentException.class, () -> Serialize.write(ByteStreams.newDataOutput(), new Object()));
	}

	@Test
	void serializeRejectsHostileInput() {
		// Deep nesting.
		Object v = 1;
		for (int i = 0; i < Serialize.MAX_DEPTH + 2; i++) v = new Object[] { v };
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		Serialize.write(out, v);
		assertThrows(IOException.class, () -> Serialize.read(new PayloadInput(out.toByteArray())));

		// A length prefix larger than the message must fail before allocating.
		ByteArrayDataOutput forged = ByteStreams.newDataOutput();
		forged.writeByte(5); // int[]
		forged.writeInt(10_000_000);
		assertThrows(IOException.class, () -> Serialize.read(new PayloadInput(forged.toByteArray())));

		// Unknown type byte, truncated input.
		assertThrows(IOException.class, () -> Serialize.read(new PayloadInput(new byte[] { 99 })));
		assertThrows(IllegalStateException.class, () -> Serialize.read(new PayloadInput(new byte[] { 1, 0, 0 })));
	}

	private static byte[] randomBytes(int n, long seed) {
		byte[] b = new byte[n];
		new Random(seed).nextBytes(b);
		return b;
	}

	@Test
	void chunkerSplitsAndReassembles() throws IOException {
		PacketChunker chunker = new PacketChunker();
		byte[] small = randomBytes(PacketChunker.INLINE_LIMIT, 1);
		byte[][] one = chunker.createPackets(small);
		assertEquals(1, one.length);
		assertSame(small, one[0], "small payloads pass through unchanged");

		byte[] big = randomBytes(100_000, 2); // incompressible: several chunks
		byte[][] chunks = chunker.createPackets(big);
		assertTrue(chunks.length >= 4, "expected several chunks, got " + chunks.length);
		for (int i = 0; i < chunks.length; i++) {
			byte[] c = chunks[i];
			assertEquals(NetworkKinds.NET_SPLITPACKET, c[0]);
			assertEquals(chunks.length, ((c[1] & 0xFF) << 8) | (c[2] & 0xFF));
			assertEquals(i, ((c[3] & 0xFF) << 8) | (c[4] & 0xFF));
			assertTrue(c.length <= PacketChunker.HEADER_SIZE + PacketChunker.CHUNK_SIZE);
			assertTrue(c.length + TRANSPORT_OVERHEAD <= SERVERBOUND_CAP, "chunk fits a serverbound custom payload");
		}

		// Two senders interleaved, chunks out of order.
		byte[] other = randomBytes(70_000, 3);
		byte[][] otherChunks = chunker.createPackets(other);
		ChunkReassembler r = new ChunkReassembler(16 * 1024 * 1024);
		List<byte[]> a = new ArrayList<>(Arrays.asList(chunks));
		Collections.reverse(a);
		byte[] gotA = null, gotB = null;
		for (int i = 0; i < Math.max(a.size(), otherChunks.length); i++) {
			if (i < a.size()) {
				byte[] res = r.accept("alice", a.get(i));
				if (res != null) gotA = res;
			}
			if (i < otherChunks.length) {
				byte[] res = r.accept("bob", otherChunks[i]);
				if (res != null) gotB = res;
			}
		}
		assertArrayEquals(big, gotA);
		assertArrayEquals(other, gotB);
		assertEquals(0, r.bufferedBytes());
		assertEquals(0, r.pendingMessages());
	}

	@Test
	void splitFramesTheKind() throws IOException {
		PacketChunker chunker = new PacketChunker();
		PacketChunker.Packet[] inline = chunker.split(NetworkKinds.NET_GPUEVENT, new byte[] { 1, 2, 3 });
		assertEquals(1, inline.length);
		assertEquals(NetworkKinds.NET_GPUEVENT, inline[0].kind());

		byte[] payload = randomBytes(200_000, 4);
		PacketChunker.Packet[] parts = chunker.split(NetworkKinds.NET_SCREENSHOT, payload);
		ChunkReassembler r = new ChunkReassembler(8 * 1024 * 1024);
		byte[] frame = null;
		for (PacketChunker.Packet p : parts) {
			assertEquals(NetworkKinds.NET_SPLITPACKET, p.kind());
			byte[] res = r.accept("p", p.payload());
			if (res != null) frame = res;
		}
		assertNotNull(frame);
		assertEquals(NetworkKinds.NET_SCREENSHOT, frame[0]);
		assertArrayEquals(payload, Arrays.copyOfRange(frame, 1, frame.length));
		assertThrows(IllegalArgumentException.class, () -> chunker.split(NetworkKinds.NET_SPLITPACKET, new byte[1]));
	}

	@Test
	void reassemblyIsBounded() throws IOException {
		long[] now = { 0 };
		PacketChunker chunker = new PacketChunker();

		// Messages over the per-message cap are dropped; their later chunks are ignored.
		ChunkReassembler r = new ChunkReassembler(50_000, 1 << 20, 4, 1_000, () -> now[0]);
		byte[][] big = chunker.createPackets(randomBytes(100_000, 5));
		r.accept("x", big[0]);
		assertThrows(IOException.class, () -> r.accept("x", big[1]));
		for (int i = 2; i < big.length; i++) assertNull(r.accept("x", big[i]));
		assertEquals(0, r.bufferedBytes());

		// Unfinished messages expire.
		byte[][] partial = chunker.createPackets(randomBytes(40_000, 6));
		assertNull(r.accept("y", partial[0]));
		assertTrue(r.bufferedBytes() > 0);
		now[0] += 2_000;
		assertNull(r.accept("z", chunker.createPackets(randomBytes(40_000, 7))[0]));
		assertEquals(1, r.pendingMessages(), "y's partial message expired, z's is pending");

		// At most maxPending unfinished messages per sender.
		ChunkReassembler few = new ChunkReassembler(1 << 20, 1 << 20, 2, Long.MAX_VALUE, () -> 0L);
		for (int i = 0; i < 5; i++) few.accept("s", chunker.createPackets(randomBytes(40_000, 10 + i))[0]);
		assertEquals(2, few.pendingMessages());

		// Duplicates and garbage are rejected.
		byte[][] dup = chunker.createPackets(randomBytes(40_000, 20));
		few.accept("d", dup[0]);
		assertThrows(IOException.class, () -> few.accept("d", dup[0]));
		assertThrows(IOException.class, () -> few.accept("d", new byte[] { 8, 0, 1 }));
		assertThrows(IOException.class, () -> few.accept("d", new byte[] { 8, 0, 1, 0, 1, 0, 0 }));

		// Decompression bombs stop at the limit.
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream zip = new GZIPOutputStream(bos)) {
			zip.write(new byte[4 * 1024 * 1024]);
		}
		byte[] bomb = bos.toByteArray();
		byte[] chunk = new byte[PacketChunker.HEADER_SIZE + bomb.length];
		chunk[0] = (byte) NetworkKinds.NET_SPLITPACKET;
		chunk[2] = 1;
		System.arraycopy(bomb, 0, chunk, PacketChunker.HEADER_SIZE, bomb.length);
		ChunkReassembler strict = new ChunkReassembler(1 << 20, 1024 * 1024, 4, Long.MAX_VALUE, () -> 0L);
		assertThrows(IOException.class, () -> strict.accept("b", chunk));
		assertEquals(0, strict.bufferedBytes());
	}

	private static List<DrawCMD> sampleCommands() {
		List<DrawCMD> cmds = new ArrayList<>();
		cmds.add(new DrawCMD(CommandEnum.SetColor, 30, 30, 60, 255));
		cmds.add(new DrawCMD(CommandEnum.Fill));
		cmds.add(new DrawCMD(CommandEnum.DrawText, "Hello ⌂", 4, 4));
		cmds.add(new DrawCMD(CommandEnum.SetLineWidth, 3.0));
		cmds.add(new DrawCMD(CommandEnum.SetAntialias, true));
		cmds.add(new DrawCMD(CommandEnum.Polygon, (Object) new int[] { 1, 2, 3, 4, 5, 6 }));
		cmds.add(new DrawCMD(CommandEnum.Import, (Object) randomBytes(5000, 8)));
		cmds.add(new DrawCMD(CommandEnum.CreateTexture, 16, 16, 1));
		cmds.add(new DrawCMD(CommandEnum.SetUniform, 1, "k", new float[] { 0.5f }, null));
		return cmds;
	}

	@Test
	void drawListRoundTrip() throws IOException {
		List<DrawCMD> cmds = sampleCommands();
		long pos = 0x0123456789ABCDEFL;
		String dim = "minecraft:the_nether";
		// A tiny batch size forces several messages; each must still decode on its own.
		List<byte[]> payloads = WireFormat.encodeDrawLists(pos, dim, cmds, 64);
		assertTrue(payloads.size() > 1);
		List<DrawCMD> back = new ArrayList<>();
		for (byte[] p : payloads) {
			PayloadInput in = new PayloadInput(p);
			WireFormat.Target t = WireFormat.readTarget(in);
			assertEquals(pos, t.pos());
			assertEquals(dim, t.dimension());
			back.addAll(WireFormat.readDrawList(in));
			assertEquals(0, in.remaining());
		}
		assertEquals(cmds.size(), back.size());
		for (int i = 0; i < cmds.size(); i++) {
			assertEquals(cmds.get(i).cmd, back.get(i).cmd);
			assertTrue(Arrays.deepEquals(cmds.get(i).args, back.get(i).args), "args of " + cmds.get(i).cmd);
		}

		// One batch when it fits, and through the chunker + reassembler as the transport would do it.
		List<byte[]> single = WireFormat.encodeDrawLists(pos, dim, cmds, WireFormat.DRAWLIST_BATCH_BYTES);
		assertEquals(1, single.size());
		PacketChunker.Packet[] parts = new PacketChunker().split(NetworkKinds.NET_GPUDRAWLIST, single.get(0));
		ChunkReassembler r = new ChunkReassembler(1 << 20);
		byte[] frame = null;
		for (PacketChunker.Packet p : parts) frame = r.accept("server", p.payload());
		assertNotNull(frame);
		PayloadInput in = new PayloadInput(frame, 1);
		assertEquals(NetworkKinds.NET_GPUDRAWLIST, frame[0]);
		WireFormat.readTarget(in);
		assertEquals(cmds.size(), WireFormat.readDrawList(in).size());
	}

	@Test
	void gpuSyncRoundTrip() throws Exception {
		TestSupport.loadFont();
		GPU server = new GPU(8192);
		Monitor mon = new Monitor(64, 48, null);
		mon.addGPU(server);
		server.processCommand(new DrawCMD(CommandEnum.SetColor, 200, 10, 10, 255));
		server.processCommand(new DrawCMD(CommandEnum.Fill));
		server.processCommand(new DrawCMD(CommandEnum.CreateTexture, 8, 4));
		server.processCommand(new DrawCMD(CommandEnum.BindTexture, 1));
		server.processCommand(new DrawCMD(CommandEnum.SetColor, 0, 255, 0, 128));
		server.processCommand(new DrawCMD(CommandEnum.FilledRectangle, 0, 0, 4, 4));
		server.processCommand(new DrawCMD(CommandEnum.Push));
		server.processCommand(new DrawCMD(CommandEnum.Translate, 3.0, 2.0));
		server.processCommand(new DrawCMD(CommandEnum.CreateShader, "uniform float k;\nvoid mainImage(out vec4 c, in vec2 f) { c = vec4(k, 0.0, 0.0, 1.0); }"));
		ShaderInstance sh = server.shaders[1];
		sh.setUniform("k", new double[] { 0.75 });

		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		WireFormat.writeTarget(out, 7L, "minecraft:overworld");
		WireFormat.writeGpuState(out, server);

		GPU client = new GPU(8192);
		client.server = false;
		Monitor cmon = new Monitor(64, 48, null);
		cmon.addGPU(client);
		PayloadInput in = new PayloadInput(out.toByteArray());
		assertEquals(7L, WireFormat.readTarget(in).pos());
		WireFormat.GpuSnapshot snap = WireFormat.readGpuState(in);
		assertEquals(0, in.remaining());
		WireFormat.applyGpuState(client, snap);

		assertArrayEquals(mon.tex.getPixels(0, 0, 64, 48), cmon.tex.getPixels(0, 0, 64, 48), "monitor pixels");
		assertNotNull(client.textures[1]);
		assertArrayEquals(server.textures[1].getPixels(0, 0, 8, 4), client.textures[1].getPixels(0, 0, 8, 4), "texture 1 pixels");
		assertEquals(1, client.bindedSlot);
		assertSame(client.textures[1], client.bindedTexture);
		assertEquals(server.state.color, client.state.color);
		assertEquals(server.state.transform, client.state.transform);
		assertEquals(1, client.state.stack.size());
		assertNotNull(client.shaders[1]);
		assertArrayEquals(server.shaders[1].values, client.shaders[1].values);
		assertEquals(server.shaders[1].source, client.shaders[1].source);
		assertSame(mon.tex, server.textures[0]);
		assertSame(cmon.tex, client.textures[0], "slot 0 stays the client's own monitor texture");
	}

	@Test
	void clientEventWhitelist() {
		assertTrue(WireFormat.isAllowedClientEvent("monitor_scroll", new Object[] { 1, 2, -1 }));
		assertTrue(WireFormat.isAllowedClientEvent("key", new Object[] { 259, false }));
		assertTrue(WireFormat.isAllowedClientEvent("key_up", new Object[] { 81 }));
		assertTrue(WireFormat.isAllowedClientEvent("char", new Object[] { "a" }));
		assertFalse(WireFormat.isAllowedClientEvent("terminate", new Object[0]));
		assertFalse(WireFormat.isAllowedClientEvent("char", new Object[] { "too long" }));
		assertFalse(WireFormat.isAllowedClientEvent("key", new Object[] { 1.0, false }));
		assertFalse(WireFormat.isAllowedClientEvent("monitor_scroll", new Object[] { 1, 2 }));
	}

	@Test
	void screenshotTable() {
		Map<Double, Double> t = WireFormat.byteTable(new byte[] { 0, (byte) 0x89, (byte) 0xFF });
		assertEquals(3, t.size());
		assertEquals(0.0, t.get(1.0));
		assertEquals(137.0, t.get(2.0));
		assertEquals(255.0, t.get(3.0));
	}
}
