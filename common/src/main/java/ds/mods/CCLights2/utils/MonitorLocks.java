package ds.mods.CCLights2.utils;

import java.util.ArrayList;
import java.util.List;

import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;

/**
 * Structural changes to a {@link Monitor} (resize, moving GPUs between screens) made from the game
 * thread while ComputerCraft threads or the client draw thread may be drawing into it. Every GPU
 * attached to the monitor is locked for the duration, the same lock GpuLuaApi and the draw thread use.
 * Only the game thread ever holds more than one GPU lock, so the nested locking cannot deadlock.
 */
public final class MonitorLocks {
	private MonitorLocks() {}

	/** Runs {@code r} while holding the lock of every GPU attached to {@code m}. */
	public static void withGpuLocks(Monitor m, Runnable r) {
		lockAll(new ArrayList<>(m.gpus), 0, r);
	}

	private static void lockAll(List<GPU> gpus, int i, Runnable r) {
		if (i >= gpus.size()) {
			r.run();
			return;
		}
		synchronized (gpus.get(i)) {
			lockAll(gpus, i + 1, r);
		}
	}

	/** Resizes a monitor's screen under the GPU locks. */
	public static void resize(Monitor m, int w, int h, boolean keepContent) {
		if (m.getWidth() == w && m.getHeight() == h) return;
		withGpuLocks(m, () -> m.resize(w, h, keepContent));
	}

	/** Moves every GPU drawing to {@code from} over to {@code to} so running programs keep a screen. */
	public static void migrateGpus(Monitor from, Monitor to) {
		if (from == to) return;
		for (GPU g : new ArrayList<>(from.gpus)) {
			synchronized (g) {
				to.addGPU(g);
				from.removeGPU(g);
			}
		}
	}

	/** Disconnects every GPU from a monitor. */
	public static void detachAll(Monitor m) {
		for (GPU g : new ArrayList<>(m.gpus)) {
			synchronized (g) {
				m.removeGPU(g);
			}
		}
	}
}
