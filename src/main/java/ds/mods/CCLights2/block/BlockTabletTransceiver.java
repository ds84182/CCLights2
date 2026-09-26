package ds.mods.CCLights2.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockTabletTransceiver extends Block {
	@SideOnly(Side.CLIENT)
	private IIcon iconFront, iconSide, iconTop;

	public BlockTabletTransceiver(Material material) {
		super(material);
		setBlockName("monitor.tablet");
		setCreativeTab(CCLights2.ccltab);
		setHardness(0.6F);
		setStepSound(Block.soundTypeMetal);
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
		world.setBlockMetadataWithNotify(x, y, z, BlockMonitor.facingFromPlacer(placer), 2);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		if (side == 1) return iconTop;
		if (side == 0) return iconSide;
		if (meta < 2) return side == 3 ? iconFront : iconSide;
		return side == meta ? iconFront : iconSide;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		iconFront = reg.registerIcon("cclights:transceiver_front");
		iconSide = reg.registerIcon("cclights:transceiver_side");
		iconTop = reg.registerIcon("cclights:transceiver_top");
	}

	@Override
	public boolean hasTileEntity(int meta) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta) {
		return new TileEntityTTrans();
	}
}
