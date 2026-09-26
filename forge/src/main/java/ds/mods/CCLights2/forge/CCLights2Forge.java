package ds.mods.CCLights2.forge;

import ds.mods.CCLights2.CCLights2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge entry point. */
@Mod(CCLights2.MOD_ID)
public class CCLights2Forge {
	@SuppressWarnings("removal") // FMLJavaModLoadingContext.get(): constructor injection needs Forge 47.4+, this works on all of 47.x
	public CCLights2Forge() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ForgePlatform.BLOCKS.register(modBus);
		ForgePlatform.ITEMS.register(modBus);
		ForgePlatform.BLOCK_ENTITY_TYPES.register(modBus);

		CCLights2.init();

		if (FMLEnvironment.dist == Dist.CLIENT) {
			modBus.addListener(CCLights2Forge::onClientSetup);
		}
	}

	private static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(CCLights2::initClient);
	}
}
