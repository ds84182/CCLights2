package ds.mods.CCLights2.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;

public class BlockMonitor extends Block {

	public BlockMonitor(int par1, Material par2Material) {
		super(par1, par2Material);
	}

    @Override
    public int getBlockTextureFromSideAndMetadata(int par1, int par2) {
        if (par1 == par2)
        {
            return 1;
        }
        else
        {
            return 255;
        }
    }

    @Override
	public boolean hasTileEntity(int meta)
	{
		return true;
	}
	
	@Override
	public TileEntity createTileEntity(World world, int meta)
	{
		return new TileEntityMonitor();
	}
	
	@Override
    public boolean onBlockActivated(World world, int x, int y, int z,
                    EntityPlayer player, int idk, float what, float these, float are) {
            TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
            if (tileEntity == null || player.isSneaking()) {
                    return false;
            }
            //code to open gui explained later
            player.openGui(CCLights2.instance, 0, world, x, y, z);
            return true;
    }
}
