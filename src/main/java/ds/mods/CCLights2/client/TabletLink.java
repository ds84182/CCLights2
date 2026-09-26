package ds.mods.CCLights2.client;

import java.util.UUID;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.item.ItemTablet;
import ds.mods.CCLights2.utils.TabMesg;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Client-side helpers to go from a tablet item to the transceiver it shows.
 */
public final class TabletLink {
	private TabletLink() {}

	/** The transceiver a tablet is paired with, if this client can see it. */
	public static TileEntityTTrans findTransceiver(ItemStack stack) {
		UUID id = ItemTablet.getTransceiverId(stack);
		if (id == null) return null;
		int[] pos = TabMesg.getPosition(id);
		World world = Minecraft.getMinecraft().theWorld;
		if (pos == null || world == null) return null;
		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if (!(te instanceof TileEntityTTrans)) return null;
		TileEntityTTrans tile = (TileEntityTTrans) te;
		return tile.id.equals(id) ? tile : null;
	}

	public static boolean inRange(TileEntityTTrans tile) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null || tile == null) return false;
		double r = Config.tabletRange;
		return player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= r * r;
	}

	/** Asks for a camera image to be sent to the paired transceiver on the next frame. */
	public static void requestScreenshot(ItemStack stack) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (ItemTablet.getTransceiverId(stack) == null) {
			say(player, "This tablet is not paired: right click a Tablet Transceiver with it first.");
			return;
		}
		TileEntityTTrans tile = findTransceiver(stack);
		if (tile == null) {
			say(player, "The paired transceiver is not loaded on this client (too far away?).");
			return;
		}
		if (!inRange(tile)) {
			say(player, "Out of range: move within " + Config.tabletRange + " blocks of the transceiver.");
			return;
		}
		ClientTickHandler.pendingScreenshot = tile;
		say(player, "Camera image sent to the transceiver at " + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord + " (tablet_image event).");
	}

	private static void say(EntityPlayer player, String msg) {
		if (player != null) player.addChatMessage(new net.minecraft.util.ChatComponentText(msg));
	}
}
