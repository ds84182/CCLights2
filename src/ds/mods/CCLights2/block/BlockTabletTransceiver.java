package ds.mods.CCLights2.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;

public class BlockTabletTransceiver extends Block {
	Icon sides = null;
	
	public BlockTabletTransceiver(int par1, Material par2Material) {
		super(par1, par2Material);
		this.setUnlocalizedName("monitor.tablet");
		this.setCreativeTab(CCLights2.ccltab);
		this.setHardness(0.6F).setStepSound(soundStoneFootstep);
	}
	
	@Override
	public void onBlockPlacedBy(World world, int par2, int par3, int par4,
			EntityLivingBase par5EntityLivingBase, ItemStack par6ItemStack) {
		if(FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER){
		int l = MathHelper.floor_double(par5EntityLivingBase.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
		int i1 = world.getBlockMetadata(par2, par3, par4) >> 2;
		++l;
		l %= 4;
		if (l == 0) {
			world.setBlockMetadataWithNotify(par2, par3, par4, 4 | i1 << 2,2);
		}

		if (l == 1) {
			world.setBlockMetadataWithNotify(par2, par3, par4, 2 | i1 << 2,2);
		}

		if (l == 2) {
			world.setBlockMetadataWithNotify(par2, par3, par4, 5 | i1 << 2,2);
		}

		if (l == 3) {
			world.setBlockMetadataWithNotify(par2, par3, par4, 3 | i1 << 2,2);
		}
		TileEntityTTrans tile = (TileEntityTTrans) world.getBlockTileEntity(par2, par3, par4);
		tile.connectToGPU();
		}
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int meta) {
			if(meta == side || side == 4 && meta == 0) {
			return this.blockIcon;
			} else {
			return sides;
			}
	}

	
	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister par1IconRegister) {
		this.blockIcon = par1IconRegister.registerIcon("cclights:tabletTfront");
	    sides = par1IconRegister.registerIcon("cclights:tabletTsides");
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
