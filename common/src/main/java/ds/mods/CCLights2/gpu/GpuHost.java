package ds.mods.CCLights2.gpu;

/**
 * What a {@link GPU} needs from whatever owns it (the GPU block entity in game, a stub in tests).
 * Keeps the gpu package free of Minecraft classes.
 */
public interface GpuHost {
	/** Queues a ComputerCraft event on every computer attached to this GPU. */
	void queueEvent(String event, Object[] args);

	/** Marks the owner as changed so it gets saved (BlockEntity.setChanged in game). */
	void markDirty();
}
