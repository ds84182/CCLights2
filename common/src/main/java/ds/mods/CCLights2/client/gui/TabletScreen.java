package ds.mods.CCLights2.client.gui;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.client.TabletLink;
import ds.mods.CCLights2.client.render.TabletTextures;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.item.TabletItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The tablet's screen (port of GuiTablet): shows the paired transceiver's monitor while it is loaded
 * and in range, a pairing hint when unpaired, and "no signal" otherwise. The transceiver is looked up
 * again every frame, so walking out of and back into range just works.
 */
public class TabletScreen extends GpuScreenBase {
	protected final ItemStack stack;
	protected final Level level;
	@Nullable
	private TabletTransceiverBlockEntity linked;

	public TabletScreen(ItemStack stack, Level level) {
		super(Component.translatable("gui.cclights.tablet"));
		this.stack = stack;
		this.level = level;
	}

	/** Resolves the paired transceiver; null when unpaired, not loaded, out of range or without a screen yet. */
	@Nullable
	private TabletTransceiverBlockEntity resolve() {
		TabletTransceiverBlockEntity tile = TabletLink.findTransceiver(stack);
		if (tile == null || !TabletLink.inRange(tile) || tile.getMonitor() == null) return null;
		return tile;
	}

	@Nullable
	@Override
	protected Texture screenTexture() {
		linked = resolve();
		if (TabletItem.getTransceiverId(stack) == null) return TabletTextures.notPaired();
		if (linked == null) return TabletTextures.noSignal();
		Monitor mon = linked.getMonitor();
		return mon == null ? TabletTextures.noSignal() : mon.tex;
	}

	@Nullable
	@Override
	protected MonitorBlockEntity inputTarget() {
		// render() refreshes the link every frame; input events in between use the latest result.
		if (linked != null && (linked.isRemoved() || !TabletLink.inRange(linked))) linked = null;
		return linked;
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (linked != null || TabletItem.getTransceiverId(stack) == null) return;
		int y = screenH > 0 ? originY + (int) Math.round(screenH * scale) + 8 : height / 2;
		if (y > height - 10) y = height - 10;
		g.drawCenteredString(font, Component.translatableWithFallback("gui.cclights.tablet.no_signal", "No signal"), width / 2, y, 0xFFFF5555);
	}
}
