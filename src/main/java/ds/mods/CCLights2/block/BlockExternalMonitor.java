package ds.mods.CCLights2.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Multi-block monitor. Rendered entirely by the tile entity renderer; clicking the screen face
 * sends a monitor click to the connected GPUs.
 */
public class BlockExternalMonitor extends Block {
	@SideOnly(Side.CLIENT)
	public static IIcon iconSide, iconBack, iconFront;

	public BlockExternalMonitor(Material material) {
		super(material);
		setBlockName("monitor.big");
		setCreativeTab(CCLights2.ccltab);
		setHardness(0.6F);
		setStepSound(Block.soundTypeMetal);
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		TileEntity te = world.getTileEntity(x, y, z);
		if (te instanceof TileEntityExternalMonitor) ((TileEntityExternalMonitor) te).destroy();
		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		TileEntity te = world.getTileEntity(x, y, z);
		if (!(te instanceof TileEntityExternalMonitor)) return false;
		TileEntityExternalMonitor tile = (TileEntityExternalMonitor) te;
		if (side != tile.getFront()) return false;
		if (player.isSneaking()) return false;
		if (world.isRemote) return true;

		// Fraction of the hit along the viewer's right-hand axis when facing the screen.
		float along;
		switch (tile.getDir()) {
		case 0: along = 1f - hitX; break; // screen faces north, viewer's right is west
		case 1: along = 1f - hitZ; break; // faces east, right is north
		case 2: along = hitX; break;      // faces south, right is east
		default: along = hitZ; break;     // faces west, right is south
		}
		int[] px = tile.hitToPixel(along, hitY);
		Monitor mon = tile.getMonitor();
		if (mon == null) return true;
		for (GPU g : mon.gpus) {
			if (g.tile == null) continue;
			g.tile.startClick(player, 0, px[0], px[1]);
			g.tile.endClick(player);
		}
		return true;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
		if (world.isRemote) return;
		TileEntity te = world.getTileEntity(x, y, z);
		if (!(te instanceof TileEntityExternalMonitor)) return;
		int l = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
		((TileEntityExternalMonitor) te).onPlaced(l);
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
	public boolean hasTileEntity(int meta) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta) {
		return new TileEntityExternalMonitor();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		return iconSide;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		blockIcon = iconSide = reg.registerIcon("cclights:monitor_big_side");
		iconBack = reg.registerIcon("cclights:monitor_big_back");
		iconFront = reg.registerIcon("cclights:monitor_big_front");
	}
}
