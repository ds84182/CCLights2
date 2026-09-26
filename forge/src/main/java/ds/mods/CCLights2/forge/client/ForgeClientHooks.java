package ds.mods.CCLights2.forge.client;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.client.CCLights2Client;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge client-only mod-bus hooks (self-registering; nothing in CCLights2Forge refers to this class).
 * Block entity renderers go through Forge's RegisterRenderers event, since the vanilla registry method
 * is private in the common jar.
 */
@Mod.EventBusSubscriber(modid = CCLights2.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientHooks {
	private ForgeClientHooks() {}

	@SubscribeEvent
	public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		CCLights2Client.registerRenderers(event::registerBlockEntityRenderer);
	}
}
