package ds.mods.CCLights2.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.client.ClientProxy;

public class ItemTablet extends Item {

	public ItemTablet(int par1) {
		super(par1-256);
		this.setMaxStackSize(1);
		this.setNoRepair();
		this.setUnlocalizedName("Tablet");
		this.setCreativeTab(ClientProxy.ccltab);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack par1ItemStack,
			EntityPlayer par2EntityPlayer, List par3List, boolean par4) {
		
	}

	@Override
	public ItemStack onItemRightClick(ItemStack par1ItemStack, World par3World, EntityPlayer par2EntityPlayer) {
		MovingObjectPosition mop = new MovingObjectPosition(par2EntityPlayer);
		int par4 = mop.blockX;
		int par5 = mop.blockY;
		int par6 = mop.blockZ;
		//TODO: Make Wireless Transmitter
		System.out.println("Output.");
		//If canDisplay then show gui end
		NBTTagCompound nbt = getNBT(par1ItemStack,par3World);
		if (nbt.getBoolean("canDisplay") & !nbt.getBoolean("gui")) //Dunno how the second part is possible, but meh
		{
			//Show GUI
			System.out.println("Show GUI");
			par2EntityPlayer.openGui(CCLights2.instance, 1, par3World, 0, 0, 0);
		}
		return par1ItemStack;
	}

	@Override
	public boolean onItemUse(ItemStack par1ItemStack,
			EntityPlayer par2EntityPlayer, World par3World, int par4, int par5,
			int par6, int par7, float par8, float par9, float par10) {
		NBTTagCompound nbt = getNBT(par1ItemStack,par3World);
		if (!par3World.isRemote && CCLights2.gpu.blockID == par3World.getBlockId(par4, par5, par6))
		{
			System.out.println("Right clicked GPU");
			nbt.setBoolean("canDisplay",true);
			nbt.setInteger("x", par4);
			nbt.setInteger("y", par5);
			nbt.setInteger("z", par6);
			return true;
		}
		return true;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack par1ItemStack) {
		return 1;
	}

	public NBTTagCompound createNBT(World par3World)
	{
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setBoolean("canDisplay", false);
		nbt.setInteger("uuid", par3World.getUniqueDataId("tablet"));
		return nbt;
	}
	
	public NBTTagCompound getNBT(ItemStack item, World parWorld)
	{
		NBTTagCompound nbt = item.getTagCompound();
		if (nbt == null)
		{
			nbt = createNBT(parWorld);
			item.setTagCompound(nbt);
		}
		return nbt;
	}
	
	@Override
	public void onCreated(ItemStack par1ItemStack, World par2World,
			EntityPlayer par3EntityPlayer) {
		par1ItemStack.setTagCompound(createNBT(par2World));
	}

	@Override
	public boolean isItemTool(ItemStack par1ItemStack)
    {
		return true;
    }
	//stuff loads faster when forge is satisfied at load
	public void registerIcons(IconRegister par1IconRegister)
    {
        this.itemIcon = par1IconRegister.registerIcon("cclights:Tablet"); // yes it errors. :P
    }
}
