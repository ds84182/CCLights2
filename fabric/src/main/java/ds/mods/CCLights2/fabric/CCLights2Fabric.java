package ds.mods.CCLights2.fabric;

import ds.mods.CCLights2.CCLights2;
import net.fabricmc.api.ModInitializer;

/** Fabric "main" entrypoint. */
public class CCLights2Fabric implements ModInitializer {
	@Override
	public void onInitialize() {
		CCLights2.init();
	}
}
