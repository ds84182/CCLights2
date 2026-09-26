package ds.mods.CCLights2.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * Takes a clean (no HUD, first person) screenshot for the tablet camera when one is requested.
 */
public class ClientTickHandler {
	/** Set by the tablet item; a screenshot is taken for this transceiver on the next render tick. */
	public static volatile TileEntityTTrans pendingScreenshot;

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase != TickEvent.Phase.START) return;
		TileEntityTTrans tile = pendingScreenshot;
		if (tile == null) return;
		pendingScreenshot = null;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null || tile.isInvalid()) return;
		GameSettings gs = mc.gameSettings;
		boolean hideGui = gs.hideGUI;
		int thirdPerson = gs.thirdPersonView;
		gs.hideGUI = true;
		gs.thirdPersonView = 0;
		try {
			mc.entityRenderer.renderWorld(event.renderTickTime, 0L);
			ClientProxy.takeScreenshot(tile);
		} finally {
			gs.hideGUI = hideGui;
			gs.thirdPersonView = thirdPerson;
		}
	}
}
