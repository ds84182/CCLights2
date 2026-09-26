package ds.mods.CCLights2.client.gui;

import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.TabletLink;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.item.ItemTablet;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Full-screen view of a tablet. Shows the paired transceiver's screen while in range,
 * a hint when unpaired, and an error when out of range or the transceiver is not loaded.
 */
public class GuiTablet extends GuiScreenBase {
	private final ItemStack stack;
	private TileEntityTTrans tile;
	private int recheck = 0;

	public GuiTablet(ItemStack stack, World world) {
		this.stack = stack;
		tile = TabletLink.findTransceiver(stack);
	}

	private boolean linked() {
		if (recheck-- <= 0) {
			recheck = 10;
			if (tile == null || tile.isInvalid()) tile = TabletLink.findTransceiver(stack);
		}
		return tile != null && !tile.isInvalid() && TabletLink.inRange(tile);
	}

	@Override
	protected Texture screenTexture() {
		if (ItemTablet.getTransceiverId(stack) == null) return TabletRenderer.defaultTexture;
		if (!linked()) return TabletRenderer.errorTexture;
		Texture tex = tile.getScreenTexture();
		return tex == null ? TabletRenderer.errorTexture : tex;
	}

	@Override
	protected TileEntityMonitor inputTarget() {
		return linked() ? tile : null;
	}
}
