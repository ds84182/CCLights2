package ds.mods.CCLights2.block;

import java.util.Random;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityAdvancedlight;

public class BlockAdvancedLight extends BlockContainer
{
    public BlockAdvancedLight(int i, Material j)
    {
        super(i, j);
        this.setLightValue(1.0F);
        this.setUnlocalizedName("Advanced.Light");
		this.setCreativeTab(CCLights2.ccltab);
		this.setHardness(0.6F).setStepSound(soundStoneFootstep);
    }
    
    public int quantityDropped(Random random)
    {
        return 1;
    }
    
    public boolean isOpaqueCube() {
	  return false;
	}
	public boolean renderAsNormalBlock() {
	  return false;
	}
	public int getRenderType() {
	  return CCLights2.proxy.modelID;
	}

    public TileEntity createNewTileEntity(World var1)
    {
        return new TileEntityAdvancedlight();
    }
    
}
