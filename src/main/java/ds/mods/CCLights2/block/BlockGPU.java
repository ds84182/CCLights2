package ds.mods.CCLights2.block;

import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.item.ItemRAM;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockGPU extends Block {
	@SideOnly(Side.CLIENT)
	private IIcon iconTop, iconBottom, iconSide;

	public BlockGPU(Material material) {
		super(material);
		setBlockName("gpu");
		setCreativeTab(CCLights2.ccltab);
		setHardness(0.6F);
		setStepSound(Block.soundTypeMetal);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		if (side == 1) return iconTop;
		if (side == 0) return iconBottom;
		return iconSide;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		iconTop = reg.registerIcon("cclights:gpu_top");
		iconBottom = reg.registerIcon("cclights:gpu_bottom");
		iconSide = reg.registerIcon("cclights:gpu_side");
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		ItemStack held = player.getHeldItem();
		if (held == null || !(held.getItem() instanceof ItemRAM)) return false;
		if (world.isRemote) return true;
		TileEntity te = world.getTileEntity(x, y, z);
		if (!(te instanceof TileEntityGPU)) return false;
		TileEntityGPU tile = (TileEntityGPU) te;
		int size = Math.min(held.getItemDamage(), ItemRAM.SIZES - 1);
		if (!player.capabilities.isCreativeMode) {
			held.stackSize--;
			if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
		}
		tile.addedType[size]++;
		tile.gpu.maxmem += (size + 1) * Config.gpuRamPerStick;
		tile.markDirty();
		player.addChatMessage(new ChatComponentText((size + 1) + "K of RAM added to GPU (" + tile.gpu.maxmem + " total)"));
		return true;
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
		TileEntity te = world.getTileEntity(x, y, z);
		if (te instanceof TileEntityGPU) ((TileEntityGPU) te).onNeighbourChanged();
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		if (!world.isRemote) {
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileEntityGPU) {
				TileEntityGPU tile = (TileEntityGPU) te;
				Random rand = world.rand;
				for (int size = 0; size < tile.addedType.length; size++) {
					int n = tile.addedType[size];
					while (n > 0) {
						int stack = Math.min(64, n);
						n -= stack;
						EntityItem item = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, new ItemStack(CCLights2.ram, stack, size));
						item.motionX = rand.nextGaussian() * 0.05F;
						item.motionY = rand.nextGaussian() * 0.05F + 0.2F;
						item.motionZ = rand.nextGaussian() * 0.05F;
						world.spawnEntityInWorld(item);
					}
				}
			}
		}
		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	public boolean hasTileEntity(int meta) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta) {
		return new TileEntityGPU();
	}
}
