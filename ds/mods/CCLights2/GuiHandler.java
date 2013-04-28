package ds.mods.CCLights2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.IGuiHandler;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.gui.GuiMonitor;
import ds.mods.CCLights2.client.gui.GuiTablet;

public class GuiHandler implements IGuiHandler {

	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world,
			int x, int y, int z) {
		TileEntity tile_entity = world.getBlockTileEntity(x, y, z);
		/*if(tile_entity instanceof TEFireFurnace){
			return new ContainerFireFurnace(player.inventory, (TEFireFurnace) tile_entity);
		}*/
		return null;
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world,
			int x, int y, int z) {
		switch (ID)
		{
		case 0:
			TileEntity tile_entity = world.getBlockTileEntity(x, y, z);
			return new GuiMonitor(((TileEntityMonitor) tile_entity));
		case 1:
			System.out.println("Tablet GUI");
			return new GuiTablet(player.getHeldItem().getTagCompound(),world);
		}
		return null;
	}

}
