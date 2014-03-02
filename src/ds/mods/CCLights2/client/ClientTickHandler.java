package ds.mods.CCLights2.client;

import java.util.EnumSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;

public class ClientTickHandler implements ITickHandler {
	public static TileEntityMonitor tile; //Invoke screenshot when this is here

	public ClientTickHandler() {
		
	}

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if (tile != null)
		{
			Minecraft mc = Minecraft.getMinecraft();
			GameSettings gs = mc.gameSettings;
			boolean hideGuiState = gs.hideGUI;
			int thirdPersonState = gs.thirdPersonView;

			int heightState = mc.displayHeight;
			int widthState = mc.displayWidth;
			gs.hideGUI = true;
			gs.thirdPersonView = 0;
			//mc.displayWidth = tile.mon.getWidth();
			//mc.displayHeight = tile.mon.getHeight();
			
			mc.entityRenderer.renderWorld(0, 0);
			
			gs.hideGUI = hideGuiState;
			gs.thirdPersonView = thirdPersonState;
			mc.displayHeight = heightState;
			mc.displayWidth = widthState;
			
			ClientProxy.takeScreenshot(tile);
			tile = null;
		}
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	public String getLabel() {
		return "CCLights2 Render Tick Tracker";
	}

}
