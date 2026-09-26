package ds.mods.CCLights2.client;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.item.TabletItem;
import ds.mods.CCLights2.utils.TabMesg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client-side helpers to go from a tablet item to the transceiver it shows. */
public final class TabletLink {
	private TabletLink() {}

	/** The transceiver a tablet is paired with, if this client has it loaded. */
	@Nullable
	public static TabletTransceiverBlockEntity findTransceiver(ItemStack stack) {
		UUID id = TabletItem.getTransceiverId(stack);
		if (id == null) return null;
		BlockPos pos = TabMesg.getPosition(id);
		ClientLevel level = Minecraft.getInstance().level;
		if (pos == null || level == null || !level.isLoaded(pos)) return null;
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof TabletTransceiverBlockEntity tile) || tile.isRemoved()) return null;
		return id.equals(tile.getId()) ? tile : null;
	}

	/** True when the local player is within {@link Config#tabletRange} blocks of the transceiver. */
	public static boolean inRange(@Nullable TabletTransceiverBlockEntity tile) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || tile == null || tile.getLevel() != player.level()) return false;
		double r = Config.tabletRange;
		BlockPos p = tile.getBlockPos();
		return player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= r * r;
	}

	/** Asks for a camera image to be sent to the paired transceiver (captured by {@link ClientTickHandler}). */
	public static void requestScreenshot(ItemStack stack) {
		if (TabletItem.getTransceiverId(stack) == null) {
			say(Component.translatable("chat.cclights.tablet.not_paired"));
			return;
		}
		TabletTransceiverBlockEntity tile = findTransceiver(stack);
		if (tile == null) {
			say(Component.translatable("chat.cclights.tablet.not_loaded"));
			return;
		}
		if (!inRange(tile)) {
			say(Component.translatable("chat.cclights.tablet.out_of_range", Config.tabletRange));
			return;
		}
		ClientTickHandler.requestScreenshot(tile);
	}

	static void say(Component msg) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) player.displayClientMessage(msg, false);
	}
}
