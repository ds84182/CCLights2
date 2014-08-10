package ds.mods.CCLights2.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.ClientTickHandler;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.utils.TabMesg;
import ds.mods.CCLights2.utils.TabMesg.Message;

public class ItemTablet extends Item {

	public ItemTablet(int par1) {
		super(par1);
		this.setMaxStackSize(1);
		this.setNoRepair();
		this.setUnlocalizedName("tablet");
		this.setCreativeTab(CCLights2.ccltab);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack item,
			EntityPlayer Player, @SuppressWarnings("rawtypes") List par3List, boolean par4) {
	}

	@Override
	public ItemStack onItemRightClick(ItemStack par1ItemStack, World par3World, EntityPlayer Player) {
		if(Player.isSneaking()){
			if (par1ItemStack.getTagCompound().getBoolean("canDisplay") && par3World.isRemote) {
				UUID trans = UUID.fromString(par1ItemStack.getTagCompound().getString("trans"));
				if(TabletRenderer.isInOfRange(trans)){
				TileEntityTTrans tile = (TileEntityTTrans) par3World.getBlockTileEntity(
								(Integer) TabMesg.getTabVar(trans, "x"),
								(Integer) TabMesg.getTabVar(trans, "y"),
								(Integer) TabMesg.getTabVar(trans, "z"));
				ClientTickHandler.tile = tile;
				}
			}
		}
		else{
			Player.openGui(CCLights2.instance, 1, par3World, 0, 0, 0);
		}
		return par1ItemStack;
	}

	@Override
	public boolean onItemUse(ItemStack par1ItemStack,
			EntityPlayer par2EntityPlayer, World par3World, int par4, int par5,
			int par6, int par7, float par8, float par9, float par10) {
		NBTTagCompound nbt = getNBT(par1ItemStack,par3World);
		
		if (!par3World.isRemote && CCLights2.ttrans.blockID == par3World.getBlockId(par4, par5, par6))
		{
			nbt.setBoolean("canDisplay",true);
			TileEntityTTrans tile = (TileEntityTTrans) par3World.getBlockTileEntity(par4, par5, par6);
			nbt.setString("trans", tile.id.toString());
			TabMesg.pushMessage(tile.id, new Message("connect",UUID.fromString(nbt.getString("uuid"))));
			return false;
		}
		return false;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack par1ItemStack) {
		return 1;
	}

	public NBTTagCompound createNBT(World par3World)
	{
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setBoolean("canDisplay", false);
		nbt.setString("uuid", UUID.randomUUID().toString());
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
	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister par1IconRegister){}
}
