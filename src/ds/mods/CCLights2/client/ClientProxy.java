package ds.mods.CCLights2.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityaAdvancedlight;
import ds.mods.CCLights2.client.render.SimpleBigMonitorRenderingHandler;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.client.render.TileEntityBigMonitorRenderer;
import ds.mods.CCLights2.client.render.TileEntityLightRenderer;

public class ClientProxy extends CommonProxy {
	public World getClientWorld()
	{
		return Minecraft.getMinecraft().theWorld;
	}
	
	public SimpleBigMonitorRenderingHandler SBMRH;
	public void registerRenderInfo()
	{
		this.modelID = RenderingRegistry.getNextAvailableRenderId();
		SBMRH = new SimpleBigMonitorRenderingHandler();
		RenderingRegistry.registerBlockHandler(SBMRH);
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBigMonitor.class, new TileEntityBigMonitorRenderer());
		MinecraftForgeClient.registerItemRenderer(CCLights2.tablet.itemID,new TabletRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityaAdvancedlight.class, new TileEntityLightRenderer());
	}
}
