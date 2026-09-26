package ds.mods.CCLights2.fabric;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.client.CCLights2Client;
import ds.mods.CCLights2.client.render.TabletItemRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Fabric "client" entrypoint; runs after the main entrypoint. */
public class CCLights2FabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CCLights2.initClient();
		// Fabric API access-widens the vanilla registry; it is read when the renderer dispatcher reloads.
		CCLights2Client.registerRenderers(BlockEntityRenderers::register);
		// The tablet's model is builtin/entity: Minecraft applies its display transforms, then this draws it.
		BuiltinItemRendererRegistry.INSTANCE.register(Registration.TABLET_ITEM.get(), TabletItemRenderer::render);
	}
}
