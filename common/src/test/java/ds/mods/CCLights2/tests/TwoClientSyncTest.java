package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.client.ClientDrawThread;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.network.PayloadInput;
import ds.mods.CCLights2.network.PendingSyncs;
import ds.mods.CCLights2.network.WireFormat;

/**
 * Simulates the server and two clients through the real wire format, draw-thread and pending-sync
 * logic (everything except the Minecraft transport, which only carries bytes). Client A watches from
 * the start; client B joins in the middle, asks for a snapshot while draw lists are already queued for
 * it, and must end up pixel-identical to the server and to A.
 */
class TwoClientSyncTest {
	private static final int W = 128, H = 72;

	/** One simulated client: a replica GPU, its monitor and the draw thread that replays for it. */
	private static final class Client {
		final GPU gpu = new GPU(8192);
		final Monitor monitor = new Monitor(W, H, null);
		final ClientDrawThread thread = new ClientDrawThread();
		boolean tracking;

		Client(boolean tracking) {
			gpu.server = false;
			monitor.addGPU(gpu);
			this.tracking = tracking;
			thread.start();
		}

		/** What ClientPacketHandlers does with a NET_GPUDRAWLIST payload. */
		void receiveDrawList(byte[] payload) throws Exception {
			if (!tracking) return;
			PayloadInput in = new PayloadInput(payload);
			WireFormat.readTarget(in);
			List<DrawCMD> cmds = WireFormat.readDrawList(in);
			int state = PendingSyncs.state(gpu, System.nanoTime());
			if (state == PendingSyncs.AWAITING) return; // contained in the coming snapshot
			assertEquals(PendingSyncs.NONE, state, "no sync request may expire in this test");
			thread.submit(gpu, cmds);
		}

		/** What ClientPacketHandlers does with a NET_GPUSYNC payload. */
		void receiveSync(byte[] payload) throws Exception {
			PayloadInput in = new PayloadInput(payload);
			WireFormat.readTarget(in);
			final WireFormat.GpuSnapshot snap = WireFormat.readGpuState(in);
			PendingSyncs.received(gpu);
			thread.submit(gpu, () -> WireFormat.applyGpuState(gpu, snap));
		}

		void drain() throws InterruptedException {
			CountDownLatch done = new CountDownLatch(1);
			thread.submit(gpu, done::countDown);
			assertTrue(done.await(20, TimeUnit.SECONDS), "draw thread drained");
		}

		/** Blocks the draw thread until the returned latch is counted down (simulates a busy client). */
		CountDownLatch block() {
			CountDownLatch gate = new CountDownLatch(1);
			thread.submit(gpu, () -> {
				try {
					gate.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException ignored) {
				}
			});
			return gate;
		}
	}

	/** The server: a GPU whose replicated commands are collected like GpuLuaApi does, then flushed per tick. */
	private static final class Server {
		final GPU gpu = new GPU(8192);
		final Monitor monitor = new Monitor(W, H, null);
		final List<DrawCMD> pending = new ArrayList<>();
		final List<Client> clients = new ArrayList<>();

		Server() {
			monitor.addGPU(gpu);
		}

		void run(CommandEnum cmd, Object... args) throws Exception {
			DrawCMD c = new DrawCMD(cmd, args);
			synchronized (gpu) {
				gpu.processCommand(c); // may rewrite args (allocated texture ids), like the real server
				if (cmd.replicated) pending.add(c);
			}
		}

		/** GpuBlockEntity.tickServer: flush the draw list to every tracking client, then answer syncs. */
		void tick(List<Client> syncRequests) throws Exception {
			synchronized (gpu) {
				if (!pending.isEmpty()) {
					for (byte[] payload : WireFormat.encodeDrawLists(1L, "minecraft:overworld", pending, WireFormat.DRAWLIST_BATCH_BYTES)) {
						for (Client c : clients) c.receiveDrawList(payload);
					}
					pending.clear();
				}
				for (Client c : syncRequests) {
					ByteArrayDataOutput out = ByteStreams.newDataOutput();
					WireFormat.writeTarget(out, 1L, "minecraft:overworld");
					WireFormat.writeGpuState(out, gpu);
					c.receiveSync(out.toByteArray());
				}
			}
		}
	}

	@Test
	void lateJoinerMatchesServerAndFirstClient() throws Exception {
		TestSupport.loadFont();
		PendingSyncs.clear();
		Server server = new Server();
		Client a = new Client(true);
		Client b = new Client(false);
		server.clients.add(a);
		server.clients.add(b);
		List<Client> none = new ArrayList<>();

		// tick 1: background, a texture, text
		server.run(CommandEnum.SetColor, 20, 30, 80, 255);
		server.run(CommandEnum.Fill);
		server.run(CommandEnum.CreateTexture, 16, 16);
		server.run(CommandEnum.BindTexture, 1);
		server.run(CommandEnum.SetColor, 255, 200, 0, 255);
		server.run(CommandEnum.FilledOval, 0, 0, 16, 16);
		server.run(CommandEnum.BindTexture, 0);
		server.run(CommandEnum.SetColor, 255, 255, 255, 255);
		server.run(CommandEnum.DrawTexture, 1, 8, 8);
		server.run(CommandEnum.DrawText, "two clients", 30, 10);
		server.tick(none);

		// B comes into range: its chunk is now tracked and it starts receiving draw lists, but its draw
		// thread is busy, so the lists queue up unexecuted...
		b.tracking = true;
		CountDownLatch gate = b.block();
		server.run(CommandEnum.Push);
		server.run(CommandEnum.Translate, 5.0, 5.0);
		server.run(CommandEnum.SetBlendMode, "add");
		server.run(CommandEnum.SetColor, 0, 90, 0, 255);
		server.run(CommandEnum.FilledRectangle, 0, 30, 100, 20); // additive: replaying twice would brighten it
		server.tick(none);

		// ...then B asks for a snapshot. The server flushes first, then snapshots (same tick, same lock).
		PendingSyncs.requested(b.gpu, System.nanoTime());
		server.run(CommandEnum.SetColor, 200, 0, 0, 255);
		server.run(CommandEnum.FilledRectangle, 40, 40, 10, 10);
		List<Client> req = new ArrayList<>();
		req.add(b);
		server.tick(req);
		gate.countDown(); // B's draw thread now runs: queued lists, then the snapshot, in order

		// tick 4: commands after the snapshot reach both clients as draw lists (transform still translated)
		server.run(CommandEnum.SetBlendMode, "normal");
		server.run(CommandEnum.SetColor, 255, 255, 255, 255);
		server.run(CommandEnum.DrawTexture, 1, 100, 40);
		server.run(CommandEnum.Pop);
		server.run(CommandEnum.SetColor, 255, 0, 255, 255);
		server.run(CommandEnum.Line, 0, 0, 127, 71);
		server.tick(none);

		a.drain();
		b.drain();

		int[] expected = server.monitor.tex.getPixels(0, 0, W, H);
		assertArrayEquals(expected, a.monitor.tex.getPixels(0, 0, W, H), "client A screen");
		assertArrayEquals(expected, b.monitor.tex.getPixels(0, 0, W, H), "client B screen (late joiner)");
		assertNotNull(b.gpu.textures[1], "B got texture 1 from the snapshot");
		assertArrayEquals(server.gpu.textures[1].getPixels(0, 0, 16, 16), b.gpu.textures[1].getPixels(0, 0, 16, 16), "B texture 1");
		assertArrayEquals(server.gpu.textures[1].getPixels(0, 0, 16, 16), a.gpu.textures[1].getPixels(0, 0, 16, 16), "A texture 1");
		assertEquals(server.gpu.state.transform, b.gpu.state.transform, "B transform after pop");
		assertEquals(server.gpu.state.color, b.gpu.state.color, "B colour");
		assertEquals(PendingSyncs.NONE, PendingSyncs.state(b.gpu, System.nanoTime()));
		a.thread.interrupt();
		b.thread.interrupt();
	}

	@Test
	void expiredSyncRequestIsReportedOnce() {
		GPU gpu = new GPU(8192);
		long now = 1_000L;
		PendingSyncs.requested(gpu, now);
		assertEquals(PendingSyncs.AWAITING, PendingSyncs.state(gpu, now + 1));
		long late = now + PendingSyncs.TIMEOUT_NANOS + 1;
		assertEquals(PendingSyncs.EXPIRED, PendingSyncs.state(gpu, late), "first look after the deadline");
		assertEquals(PendingSyncs.NONE, PendingSyncs.state(gpu, late), "reported only once");
	}
}
