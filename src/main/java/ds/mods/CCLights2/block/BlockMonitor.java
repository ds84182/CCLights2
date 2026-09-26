package ds.mods.CCLights2.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.GuiHandler;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
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
 * Single-block monitor. Metadata stores the side index (2..5) the screen faces.
 */
public class BlockMonitor extends Block {
	@SideOnly(Side.CLIENT)
	protected IIcon iconFront, iconBack, iconSide, iconTop;

	public BlockMonitor(Material material) {
		super(material);
		setBlockName("monitor.normal");
		setCreativeTab(CCLights2.ccltab);
		setHardness(0.6F);
		setStepSound(Block.soundTypeMetal);
	}

	/** Side index the placer is looking at: the block's front faces the player. */
	public static int facingFromPlacer(EntityLivingBase placer) {
		int l = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
		switch (l) {
		case 0: return 2; // player faces south, block faces north (towards player)
		case 1: return 5;
		case 2: return 3;
		default: return 4;
		}
	}

	public static int opposite(int side) {
		return side ^ 1;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
		world.setBlockMetadataWithNotify(x, y, z, facingFromPlacer(placer), 2);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		if (side == 0 || side == 1) return iconTop;
		if (meta < 2) return side == 3 ? iconFront : (side == 2 ? iconBack : iconSide); // inventory
		if (side == meta) return iconFront;
		if (side == opposite(meta)) return iconBack;
		return iconSide;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		iconFront = reg.registerIcon("cclights:monitor_front");
		iconBack = reg.registerIcon("cclights:monitor_back");
		iconSide = reg.registerIcon("cclights:monitor_side");
		iconTop = reg.registerIcon("cclights:monitor_top");
	}

	@Override
	public boolean hasTileEntity(int meta) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta) {
		return new TileEntityMonitor();
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if (player.isSneaking()) return false;
		TileEntity tile = world.getTileEntity(x, y, z);
		if (!(tile instanceof TileEntityMonitor)) return false;
		// Client-side open: FML only opens server-requested GUIs that come with a Container.
		if (world.isRemote) player.openGui(CCLights2.instance, GuiHandler.GUI_MONITOR, world, x, y, z);
		return true;
	}
}
