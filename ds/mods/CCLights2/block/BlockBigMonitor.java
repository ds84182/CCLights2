package ds.mods.CCLights2.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.Player;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;

public class BlockBigMonitor extends Block {

	public BlockBigMonitor(int par1, Material par2Material) {
		super(par1, par2Material);
	}
	
	@Override
	public void breakBlock(World par1World, int par2, int par3, int par4,
			int par5, int par6) {
		TileEntityBigMonitor tile = (TileEntityBigMonitor) par1World.getBlockTileEntity(par2, par3, par4);
		tile.destroy();
		super.breakBlock(par1World, par2, par3, par4, par5, par6);
	}

	@Override
	public boolean onBlockActivated(World par1World, int par2, int par3,
			int par4, EntityPlayer par5EntityPlayer, int par6, float par7,
			float par8, float par9) {
		TileEntityBigMonitor tile = (TileEntityBigMonitor) par1World.getBlockTileEntity(par2,par3,par4);
		System.out.println(par7+","+par8+","+par9);
		float x = 0f;
		float y = 0f;
		switch (tile.m_dir)
		{
			case 2:
			{
				if (par9 == 1.0f)
				{
					x = par7;
					y = par8;
				}
				else
				{
					return false;
				}
				break;
			}
		}
		int px = Math.round(x*31);
		int py = Math.round((1-y)*31);
		px+=(tile.m_width-tile.m_xIndex-1)*32;
		py+=(tile.m_height-tile.m_yIndex-1)*32;
		System.out.println(px+","+py);
		if (!par1World.isRemote)
		{
			//Send it to the tileentity!
			if (tile.mon != null && tile.mon.gpu != null && tile.mon.gpu.tile != null)
			{
				tile.mon.gpu.tile.startClick((Player) par5EntityPlayer, 0, px, py);
				tile.mon.gpu.tile.endClick((Player) par5EntityPlayer);
			}
		}
		return true;
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess par1iBlockAccess,
			int par2, int par3, int par4) {
		TileEntityBigMonitor tile = (TileEntityBigMonitor) par1iBlockAccess.getBlockTileEntity(par2, par3, par4);
		this.minX = -Math.abs(tile.m_xIndex-tile.m_width)+1D;
		this.minY = -tile.m_yIndex;
		this.maxX = 1D+tile.m_xIndex;
		this.maxY = 1D+(tile.m_height-tile.m_yIndex-1);
	}

	@Override
	public boolean hasTileEntity(int meta)
	{
		return true;
	}
	
	@Override
	public TileEntity createTileEntity(World w, int meta)
	{
		return new TileEntityBigMonitor();
	}
	
	@Override
	public void onBlockPlacedBy(World world, int i, int j, int k, EntityLiving entityliving, ItemStack item)
	{
		TileEntityBigMonitor tile = (TileEntityBigMonitor) world.getBlockTileEntity(i, j, k);
		System.out.println("Placed.");
		tile.contractNeighbours();
        //monitor.setDir(dir);
        tile.contract();
        tile.expand();
	}
	
	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public int getRenderType() {
		return CCLights2.proxy.modelID;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

}
