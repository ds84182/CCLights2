package ds.mods.CCLights2.client;

import java.util.EnumSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;

public class ClientTickHandler implements ITickHandler {
	public static TileEntityTTrans tile; //Invoke screenshot when this is here

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if (tile != null)
		{
			//get minecraft settings
			Minecraft mc = Minecraft.getMinecraft();
			GameSettings gs = mc.gameSettings;
			boolean hideGuiState = gs.hideGUI;
			int thirdPersonState = gs.thirdPersonView;
			//start render instance
			gs.hideGUI = true;
			gs.thirdPersonView = 0;
			mc.entityRenderer.renderWorld(0, 0);
			gs.hideGUI = hideGuiState;
			gs.thirdPersonView = thirdPersonState;
			//most of render we need is done, lets take a picture :DDDDDDDD
			ClientProxy.takeScreenshot(tile);
			//reset tileent so we can use it again.
			tile = null;
		}
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	public String getLabel() {
		return "CCLights2 Render Tick Tracker";
	}
    
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {}
}
