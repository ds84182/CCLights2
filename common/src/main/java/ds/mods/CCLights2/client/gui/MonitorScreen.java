package ds.mods.CCLights2.client.gui;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import net.minecraft.network.chat.Component;

/** Full-screen view of a monitor that forwards mouse and keys to the GPU (port of GuiMonitor). */
public class MonitorScreen extends GpuScreenBase {
	protected final MonitorBlockEntity monitor;

	public MonitorScreen(MonitorBlockEntity monitor) {
		super(Component.translatable("gui.cclights.monitor"));
		this.monitor = monitor;
	}

	@Nullable
	@Override
	protected Texture screenTexture() {
		if (monitor.isRemoved()) return null;
		Monitor mon = monitor.getMonitor();
		return mon == null ? null : mon.tex;
	}

	@Nullable
	@Override
	protected MonitorBlockEntity inputTarget() {
		return monitor.isRemoved() ? null : monitor;
	}

	@Override
	public void tick() {
		super.tick();
		if (minecraft == null || minecraft.player == null) return;
		if (monitor.isRemoved() || !monitor.canInteract(minecraft.player)) onClose();
	}
}
