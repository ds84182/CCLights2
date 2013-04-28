package ds.mods.CCLights2.client;

import java.io.IOException;

import javax.xml.stream.util.EventReaderDelegate;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.client.TextureFXManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.client.render.SimpleBigMonitorRenderingHandler;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.client.render.TileEntityBigMonitorRenderer;

public class ClientProxy extends CommonProxy {
	public World getClientWorld()
	{
		return Minecraft.getMinecraft().theWorld;
	}
	
	public SimpleBigMonitorRenderingHandler SBMRH;
	
	public void registerRenderInfo()
	{
		//MinecraftForgeClient.preloadTexture("/ds/mods/CCLights2/texture/terrain.png");
		//MinecraftForgeClient.preloadTexture("/ds/mods/CCLights2/texture/GPUFX.png");
		//MinecraftForgeClient.preloadTexture("/ds/mods/CCLights2/texture/items.png");
		/*try {
			TextureFXManager.instance().addAnimation(new TextureAnimatedFX("/ds/mods/CCLights2/texture/GPUFX.png",0));
		} catch (IOException e) {
			System.err.println("[CCLights2] Error registering animation with FML: " + e.getMessage());
		}*/
		//Now, we should register the Block Renderer for TileEntityBigMonitor
		this.modelID = RenderingRegistry.getNextAvailableRenderId();
		SBMRH = new SimpleBigMonitorRenderingHandler();
		RenderingRegistry.registerBlockHandler(SBMRH);
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBigMonitor.class, new TileEntityBigMonitorRenderer());
		MinecraftForgeClient.registerItemRenderer(CCLights2.tablet.itemID,new TabletRenderer());
		MinecraftForge.EVENT_BUS.register(new Events());
	}
}
