package ds.mods.CCLights2.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;

public class BlockTabletTransceiver extends Block {
	@SideOnly(Side.CLIENT)
	Icon sides = null;
	
	public BlockTabletTransceiver(int par1, Material par2Material) {
		super(par1, par2Material);
		this.setUnlocalizedName("monitor.tablet");
		this.setCreativeTab(CCLights2.ccltab);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int meta) {
			if(side == 2) {
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
