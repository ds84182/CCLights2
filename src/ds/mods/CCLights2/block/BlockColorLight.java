package ds.mods.CCLights2.block;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.ILuaObject;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityColorLight;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityaAdvancedlight;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans.MonitorObject;
import ds.mods.CCLights2.converter.ConvertInteger;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.utils.TabMesg;
import ds.mods.CCLights2.utils.TabMesg.Message;

public class BlockColorLight extends Block {

	public BlockColorLight(int par1, Material par2Material) {
		super(par1, par2Material);
		this.setUnlocalizedName("Light");
		this.setLightValue(1.0F);
		this.setHardness(0.6F).setStepSound(soundStoneFootstep);
		this.setCreativeTab(CreativeTabs.tabBlock);
	}

	@SideOnly(Side.CLIENT)
	public Icon getIcon(int par1, int par2) {
		return this.blockIcon;
	}
	
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister par1IconRegister) {
       par1IconRegister.registerIcon("CCLights:light");
	}
	public int quantityDropped(Random random)
    {
        return 1;
    }

    public TileEntity createNewTileEntity(World var1)
    {
        return new TileEntityColorLight();
    }
}
