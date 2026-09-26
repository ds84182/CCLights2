package ds.mods.CCLights2.utils;

import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ds.mods.CCLights2.CCLights2;

/**
 * Minecraft 1.7.10 has no scheduled-task API on the server, so packets are queued here
 * and drained at the start of every server tick.
 */
public class ServerTaskQueue {
	private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();

	public void submit(Runnable r) {
		tasks.add(r);
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.START) return;
		Runnable r;
		while ((r = tasks.poll()) != null) {
			try {
				r.run();
			} catch (Exception e) {
				CCLights2.logger.warn("CCLights2 server task failed: " + e);
			}
		}
	}
}
