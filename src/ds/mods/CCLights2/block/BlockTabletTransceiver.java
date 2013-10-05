package ds.mods.CCLights2.block;

import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockTabletTransceiver extends Block {

	public BlockTabletTransceiver(int par1, Material par2Material) {
		super(par1, par2Material);
	}

	@Override
	public boolean hasTileEntity(int metadata) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int metadata) {
		return new TileEntityTTrans();
	}

}
