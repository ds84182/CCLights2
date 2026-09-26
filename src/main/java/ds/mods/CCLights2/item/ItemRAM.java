package ds.mods.CCLights2.item;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * GPU memory upgrade. Damage value + 1 is the size in K.
 */
public class ItemRAM extends Item {
	public static final int SIZES = 8;

	public ItemRAM() {
		setHasSubtypes(true);
		setUnlocalizedName("ram");
		setCreativeTab(CCLights2.ccltab);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		int size = Math.min(stack.getItemDamage(), SIZES - 1) + 1;
		lines.add(size + "K (" + (size * Config.gpuRamPerStick) + " texture units)");
		lines.add("Right click a GPU to install");
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tab, List list) {
		for (int i = 0; i < SIZES; i++) list.add(new ItemStack(item, 1, i));
	}

	@Override
	public String getUnlocalizedName(ItemStack stack) {
		return super.getUnlocalizedName(stack);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister reg) {
		itemIcon = reg.registerIcon("cclights:ram");
	}
}
