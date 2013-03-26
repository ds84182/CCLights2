package ds.mods.CCLights2.client;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import cpw.mods.fml.client.TextureFXManager;
import ds.mods.CCLights2.CommonProxy;

public class ClientProxy extends CommonProxy {
	public World getClientWorld()
	{
		return Minecraft.getMinecraft().theWorld;
	}
	
	public void registerRenderInfo()
	{
		MinecraftForgeClient.preloadTexture("/ds/mods/CCLights2/texture/terrain.png");
		MinecraftForgeClient.preloadTexture("/ds/mods/CCLights2/texture/GPUFX.png");
		MinecraftForgeClient.preloadTexture("/ds/mods/CCLights2/texture/items.png");
		try {
			TextureFXManager.instance().addAnimation(new TextureAnimatedFX("/ds/mods/CCLights2/texture/GPUFX.png",0));
		} catch (IOException e) {
			System.err.println("[CCLights2] Error registering animation with FML: " + e.getMessage());
		}
	}
}
