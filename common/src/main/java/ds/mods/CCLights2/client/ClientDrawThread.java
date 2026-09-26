package ds.mods.CCLights2.client;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;

/**
 * Executes replicated draw commands off the render thread. Java2D work (blurs, text, big fills)
 * would otherwise stall frames. Results are published through {@link GPU#updateMonitors()}.
 */
public class ClientDrawThread extends Thread {
	private static final class Job {
		final GPU gpu;
		final List<DrawCMD> cmds;

		Job(GPU gpu, List<DrawCMD> cmds) {
			this.gpu = gpu;
			this.cmds = cmds;
		}
	}

	private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<Job>();

	public ClientDrawThread() {
		super("CCLights2 Draw Thread");
		setDaemon(true);
		setPriority(Thread.NORM_PRIORITY - 1);
	}

	public void submit(GPU gpu, List<DrawCMD> cmds) {
		queue.add(new Job(gpu, cmds));
	}

	/** Drops everything still queued, e.g. when the world is unloaded. */
	public void clear() {
		queue.clear();
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			Job job;
			try {
				job = queue.take();
			} catch (InterruptedException e) {
				return;
			}
			GPU gpu = job.gpu;
			synchronized (gpu) {
				for (DrawCMD cmd : job.cmds) {
					try {
						gpu.processCommand(cmd);
					} catch (Exception e) {
						if (Config.DEBUG) CCLights2.LOGGER.warn("Client failed to replay " + cmd.cmd + ": " + e);
					}
				}
				gpu.updateMonitors();
			}
		}
	}
}
