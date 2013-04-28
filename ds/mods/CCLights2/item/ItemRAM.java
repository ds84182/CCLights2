package ds.mods.CCLights2.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemRAM extends Item {

	public ItemRAM(int par1) {
		super(par1);
		//this.setTextureFile("/ds/mods/CCLights2/texture/items.png");
		//this.iconIndex = 0;
		this.hasSubtypes = true;
		//this.setItemName("RAM");
	}
	
	public void addInformation(ItemStack par1ItemStack, EntityPlayer par2EntityPlayer, List par3List, boolean par4)
	{
		int ramammt = (par1ItemStack.getItemDamage()+1);
		par3List.add(ramammt+"K");
	}
	
	public void getSubItems(int par1, CreativeTabs par2CreativeTabs, List par3List)
    {
		super.getSubItems(par1, par2CreativeTabs, par3List);
		for (int i = 1; i<8; i++)
		{
			par3List.add(new ItemStack(par1, 1, i));
		}
    }

}
