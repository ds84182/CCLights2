package ds.mods.CCLights2.item;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The tablet (port of ItemTablet): a hand-held screen paired to a tablet transceiver. Using it on a
 * transceiver pairs it (transceiver UUID in the stack NBT); using it in the air opens the tablet
 * screen, sneak + use sends a camera image to the transceiver's computers (tablet_image event).
 */
public class TabletItem extends Item {
	/** This tablet's own id (what the transceiver lists in getTabletUUID). */
	public static final String NBT_UUID = "uuid";
	/** The paired transceiver's id. */
	public static final String NBT_TRANS = "trans";
	public static final String NBT_CAN_DISPLAY = "canDisplay";

	public TabletItem() {
		super(new Item.Properties().stacksTo(1));
	}

	/** The paired transceiver's id, or null when the tablet is not paired. */
	@Nullable
	public static UUID getTransceiverId(ItemStack stack) {
		CompoundTag nbt = stack.getTag();
		if (nbt == null || !nbt.getBoolean(NBT_CAN_DISPLAY) || !nbt.contains(NBT_TRANS, Tag.TAG_STRING)) return null;
		try {
			return UUID.fromString(nbt.getString(NBT_TRANS));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Pairs the stack with a transceiver id (null unpairs). Also gives the tablet its own id if it has none. */
	public static void setTransceiverId(ItemStack stack, @Nullable UUID transceiver) {
		CompoundTag nbt = getOrCreateNbt(stack);
		if (transceiver == null) {
			nbt.remove(NBT_TRANS);
			nbt.putBoolean(NBT_CAN_DISPLAY, false);
		} else {
			nbt.putString(NBT_TRANS, transceiver.toString());
			nbt.putBoolean(NBT_CAN_DISPLAY, true);
		}
	}

	/** This tablet's own id, created on first use. Call on the server so the id is authoritative. */
	public static UUID getOrCreateTabletId(ItemStack stack) {
		CompoundTag nbt = getOrCreateNbt(stack);
		try {
			return UUID.fromString(nbt.getString(NBT_UUID));
		} catch (IllegalArgumentException e) {
			UUID id = UUID.randomUUID();
			nbt.putString(NBT_UUID, id.toString());
			return id;
		}
	}

	/** This tablet's own id, or null if it has none yet (read-only; safe on the client). */
	@Nullable
	public static UUID getTabletId(ItemStack stack) {
		CompoundTag nbt = stack.getTag();
		if (nbt == null || !nbt.contains(NBT_UUID, Tag.TAG_STRING)) return null;
		try {
			return UUID.fromString(nbt.getString(NBT_UUID));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static CompoundTag getOrCreateNbt(ItemStack stack) {
		CompoundTag nbt = stack.getOrCreateTag();
		if (!nbt.contains(NBT_UUID, Tag.TAG_STRING)) {
			nbt.putString(NBT_UUID, UUID.randomUUID().toString());
			if (!nbt.contains(NBT_CAN_DISPLAY)) nbt.putBoolean(NBT_CAN_DISPLAY, false);
		}
		return nbt;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			if (player.isShiftKeyDown()) Client.requestScreenshot(stack);
			else Client.openTabletScreen(stack);
		} else {
			getOrCreateTabletId(stack);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof TabletTransceiverBlockEntity transceiver)) return InteractionResult.PASS;
		if (level.isClientSide) return InteractionResult.SUCCESS;
		ItemStack stack = context.getItemInHand();
		UUID tablet = getOrCreateTabletId(stack);
		setTransceiverId(stack, transceiver.getId());
		transceiver.connectTablet(tablet);
		Player player = context.getPlayer();
		if (player != null) player.displayClientMessage(Component.translatable("chat.cclights.tablet.paired"), false);
		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
		if (getTransceiverId(stack) != null) {
			lines.add(Component.translatable("tooltip.cclights.tablet.paired").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.cclights.tablet.camera").withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.translatable("tooltip.cclights.tablet.unpaired").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Keeps client-only classes out of the server: loaded only when a client uses the tablet. */
	private static final class Client {
		static void openTabletScreen(ItemStack stack) {
			ds.mods.CCLights2.client.ClientAccess.openTabletScreen(stack);
		}

		static void requestScreenshot(ItemStack stack) {
			ds.mods.CCLights2.client.TabletLink.requestScreenshot(stack);
		}
	}
}
