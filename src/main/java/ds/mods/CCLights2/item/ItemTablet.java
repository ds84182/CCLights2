package ds.mods.CCLights2.item;

import java.util.List;
import java.util.UUID;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.GuiHandler;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.TabletLink;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * Hand-held screen paired to a tablet transceiver. Right click opens it, sneak + right click
 * sends a screenshot from the player's view to the transceiver's computer (tablet_image event).
 */
public class ItemTablet extends Item {
	public static final String NBT_UUID = "uuid";
	public static final String NBT_TRANS = "trans";
	public static final String NBT_CAN_DISPLAY = "canDisplay";

	public ItemTablet() {
		setMaxStackSize(1);
		setNoRepair();
		setUnlocalizedName("tablet");
		setCreativeTab(CCLights2.ccltab);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt != null && nbt.getBoolean(NBT_CAN_DISPLAY)) {
			lines.add("Paired with a transceiver");
			lines.add("Sneak + right click: send camera image");
		} else {
			lines.add("Right click a Tablet Transceiver to pair");
		}
	}

	public static UUID getTransceiverId(ItemStack stack) {
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null || !nbt.getBoolean(NBT_CAN_DISPLAY)) return null;
		try {
			return UUID.fromString(nbt.getString(NBT_TRANS));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		getNBT(stack);
		if (world.isRemote) {
			if (player.isSneaking()) TabletLink.requestScreenshot(stack);
			else player.openGui(CCLights2.instance, GuiHandler.GUI_TABLET, world, 0, 0, 0);
		}
		return stack;
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		TileEntity te = world.getTileEntity(x, y, z);
		if (!(te instanceof TileEntityTTrans)) return false;
		if (world.isRemote) return true;
		TileEntityTTrans tile = (TileEntityTTrans) te;
		NBTTagCompound nbt = getNBT(stack);
		nbt.setBoolean(NBT_CAN_DISPLAY, true);
		nbt.setString(NBT_TRANS, tile.id.toString());
		tile.connectTablet(UUID.fromString(nbt.getString(NBT_UUID)));
		player.addChatMessage(new ChatComponentText("Tablet paired with transceiver"));
		return true;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 1;
	}

	public NBTTagCompound getNBT(ItemStack stack) {
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null) {
			nbt = new NBTTagCompound();
			stack.setTagCompound(nbt);
		}
		if (!nbt.hasKey(NBT_UUID)) {
			nbt.setString(NBT_UUID, UUID.randomUUID().toString());
			nbt.setBoolean(NBT_CAN_DISPLAY, false);
		}
		return nbt;
	}

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player) {
		getNBT(stack);
	}

	@Override
	public boolean isItemTool(ItemStack stack) {
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister reg) {
		// The item renderer draws the 3D model; no icon needed.
	}
}
