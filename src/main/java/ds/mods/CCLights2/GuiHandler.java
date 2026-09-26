package ds.mods.CCLights2;

import cpw.mods.fml.common.network.IGuiHandler;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.gui.GuiMonitor;
import ds.mods.CCLights2.client.gui.GuiTablet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class GuiHandler implements IGuiHandler {
	public static final int GUI_MONITOR = 0;
	public static final int GUI_TABLET = 1;

	@Override
	public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}

	@Override
	public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
		switch (id) {
		case GUI_MONITOR: {
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile instanceof TileEntityMonitor) return new GuiMonitor((TileEntityMonitor) tile);
			return null;
		}
		case GUI_TABLET: {
			ItemStack held = player.getHeldItem();
			if (held == null || held.getItem() != CCLights2.tablet) return null;
			return new GuiTablet(held, world);
		}
		default:
			return null;
		}
	}
}
