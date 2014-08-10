package ds.mods.CCLights2.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.Player;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.gpu.GPU;

public class BlockExternalMonitor extends Block {
	public BlockExternalMonitor(int par1, Material par2Material) {
		super(par1, par2Material);
		this.setUnlocalizedName("monitor.big");
		this.setCreativeTab(CCLights2.ccltab);
		this.setHardness(0.6F).setStepSound(soundStoneFootstep);
	}
	
	@Override
	public void breakBlock(World par1World, int par2, int par3, int par4, int par5, int par6) {
		TileEntityExternalMonitor tile = (TileEntityExternalMonitor) par1World.getBlockTileEntity(par2, par3, par4);
		tile.destroy();
		super.breakBlock(par1World, par2, par3, par4, par5, par6);
	}

	@Override
	public boolean onBlockActivated(World world, int par2, int par3, int par4, EntityPlayer par5EntityPlayer, int par6, float vecX,
			float vecY, float vecZ) {
		TileEntityExternalMonitor tile = (TileEntityExternalMonitor) world.getBlockTileEntity(par2,par3,par4);
		float x = 0f;
		float y = 0f;
		switch (tile.m_dir)
		{
			case 0:
			{
				if (vecZ == 0.0f)
				{
					x = 1F-vecX;
					y = vecY;
				}
				else
				{
					return false;
				}
				break;
			}
			case 1:
			{
				if (vecX == 1.0f)
				{
					x = vecY;
					y = vecZ;
				}
				else
				{
					return false;
				}
				break;
			}
			case 2:
			{
				if (vecZ == 1.0f)
				{
					x = vecX;
					y = vecY;
				}
				else
				{
					return false;
				}
				break;
			}
			case 3:
			{
				if (vecX == 0.0f)
				{
					x = vecY;
					y = vecZ;
				}
				else
				{
					return false;
				}
				break;
			}
		}
		int px = (int) Math.floor(x*32F);
		int py = (int) Math.floor((1F-y)*32F);
		px+=(tile.m_width-tile.m_xIndex-1)*32;
		py+=(tile.m_height-tile.m_yIndex-1)*32;
		if (!world.isRemote)
		{
			//Send it to the tileentity!
			if (tile.mon != null && tile.mon.gpu != null)
			{
				for (GPU g : tile.mon.gpu)
				{
					g.tile.startClick((Player) par5EntityPlayer, 0, px, py);
					g.tile.endClick((Player) par5EntityPlayer);
				}
			}
		}
		return true;
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess par1iBlockAccess, int par2, int par3, int par4) {
		setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
	}

	@Override
	public boolean hasTileEntity(int meta)
	{
		return true;
	}
	
	@Override
	public TileEntity createTileEntity(World w, int meta)
	{
		return new TileEntityExternalMonitor();
	}
	
	@Override
	public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entityliving, ItemStack item)
	{
		if(!world.isRemote){
		int l = MathHelper.floor_double(entityliving.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
		TileEntityExternalMonitor tile = (TileEntityExternalMonitor) world.getBlockTileEntity(i, j, k);
		tile.setDir(l);
		tile.contractNeighbours();
        tile.contract();
        tile.expand();
		}
	}
	
	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public int getRenderType() {
		return CommonProxy.modelID;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}
	
	 @Override
	  public void registerIcons(IconRegister iconRegister) {
	      blockIcon = iconRegister.registerIcon("cclights:monitorsides");
	  }
}
