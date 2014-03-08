package ds.mods.CCLights2.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.item.ItemRAM;

public class BlockGPU extends Block {
	Icon sides = null;
	public BlockGPU(int par1, Material par2Material) {
		super(par1, par2Material);
		this.setUnlocalizedName("gpu");
		this.setCreativeTab(CCLights2.ccltab);
		this.setHardness(0.6F).setStepSound(soundStoneFootstep);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int meta) {
		if (side == 0 || side == 1) {
			return this.blockIcon;
		}
		return sides;
	}

	@Override
	public boolean onBlockActivated(World par1World, int par2, int par3,
			int par4, EntityPlayer par5EntityPlayer, int par6, float par7,
			float par8, float par9) {
		ItemStack curr = par5EntityPlayer.getHeldItem();
		if (curr == null)
			return false;
		if (curr.getItem() instanceof ItemRAM) {
			if (!par5EntityPlayer.capabilities.isCreativeMode) {
				curr.stackSize--;
			}
			TileEntityGPU tile = (TileEntityGPU) par1World.getBlockTileEntity(
					par2, par3, par4);
			tile.addedType[curr.getItemDamage()]++;
			tile.gpu.maxmem += 1024 * (curr.getItemDamage() + 1);
			if (par1World.isRemote) {
				par5EntityPlayer.addChatMessage((curr.getItemDamage() + 1)
						+ "K of RAM added to GPU");
			}
			return true;
		}
		return false;
	}

	@Override
	public void breakBlock(World par1World, int par2, int par3, int par4,
			int par5, int par6) {
		if (!par1World.isRemote) {
			Random rand = new Random();
			TileEntityGPU tile = (TileEntityGPU) par1World.getBlockTileEntity(par2, par3, par4);
			if(tile != null && tile.addedType != null){
			for (int i = 0; i < tile.addedType.length; i++) {
				int n = tile.addedType[i];
				while (n != 0) {
					int stacksize = 0;
					if (n > 64) {
						stacksize = 64;
					} else {
						stacksize = n;
					}
					n -= stacksize;
					EntityItem var14 = new EntityItem(par1World,
							par2 + 0.5,
							par3 + 0.5,
							par4 + 0.5, new ItemStack(
									CCLights2.ram, stacksize, i));
					float var15 = 0.05F;
					var14.motionX = (float) rand.nextGaussian() * var15;
					var14.motionY = (float) rand.nextGaussian()
							* var15 + 0.2F;
					var14.motionZ = (float) rand.nextGaussian() * var15;
					par1World.spawnEntityInWorld(var14);
				}
			}
			}
		}
		super.breakBlock(par1World, par2, par3, par4, par5, par6);
	}
	@Override
	public void onNeighborBlockChange(World par1World, int par2, int par3, int par4, int par5) {
		TileEntityGPU tile = (TileEntityGPU) par1World.getBlockTileEntity(par2, par3, par4);
		if(tile != null) tile.connectToMonitor();
	}

	@Override
	public boolean hasTileEntity(int meta) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta) {
		return new TileEntityGPU();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister par1IconRegister) {
		this.blockIcon = par1IconRegister.registerIcon("cclights:gpufront");
		sides = par1IconRegister.registerIcon("cclights:gpusides");
	}

}
