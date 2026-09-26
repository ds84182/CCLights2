package ds.mods.CCLights2.client.gui;

import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.gpu.Texture;

public class GuiMonitor extends GuiScreenBase {
	private final TileEntityMonitor tile;

	public GuiMonitor(TileEntityMonitor tile) {
		this.tile = tile;
	}

	@Override
	protected Texture screenTexture() {
		return tile.isInvalid() ? null : tile.getScreenTexture();
	}

	@Override
	protected TileEntityMonitor inputTarget() {
		return tile.isInvalid() ? null : tile;
	}

	@Override
	public void updateScreen() {
		if (mc.thePlayer == null) return;
		if (tile.isInvalid() || !tile.canInteract(mc.thePlayer)) mc.thePlayer.closeScreen();
	}
}
