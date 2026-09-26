package ds.mods.CCLights2.client;

import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.client.gui.MonitorScreen;
import ds.mods.CCLights2.client.gui.TabletScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Entry points for common code that must do something client-only. Only call these on the physical
 * client (guard with {@code level.isClientSide()}); the class is never loaded on a dedicated server
 * as long as the call sites are not reached there.
 */
public final class ClientAccess {
	private ClientAccess() {}

	public static void openMonitorScreen(MonitorBlockEntity be) {
		Minecraft.getInstance().setScreen(new MonitorScreen(be));
	}

	public static void openTabletScreen(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		mc.setScreen(new TabletScreen(stack, mc.level));
	}

	/** Tablet camera: validates pairing/range with chat feedback and captures on a following tick. */
	public static void requestTabletScreenshot(ItemStack stack) {
		TabletLink.requestScreenshot(stack);
	}
}
