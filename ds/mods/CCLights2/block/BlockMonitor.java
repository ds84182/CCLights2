package ds.mods.CCLights2.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.ClientProxy;

public class BlockMonitor extends Block {
	
	Icon sides = null;

	public BlockMonitor(int par1, Material par2Material) {
		super(par1, par2Material);
		this.setUnlocalizedName("Monitor");
		this.setCreativeTab(ClientProxy.ccltab);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int meta) {
		return ForgeDirection.getOrientation(side) == ForgeDirection.NORTH ? this.blockIcon : sides;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister par1IconRegister) {
		this.blockIcon = par1IconRegister.registerIcon("cclights:monitor");
		sides = par1IconRegister.registerIcon("cclights:blank");
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
