package ds.mods.CCLights2.forge;

import java.util.function.Consumer;

import ds.mods.CCLights2.forge.client.ForgeTabletRenderer;
import ds.mods.CCLights2.item.TabletItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Forge attaches custom item renderers through {@link net.minecraft.world.item.Item#initializeClient}, so the
 * tablet is this subclass on Forge. Forge only calls initializeClient on the client, and the extension
 * object is created lazily, so no client class is loaded on a dedicated server.
 */
public class ForgeTabletItem extends TabletItem {
	@Override
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(new IClientItemExtensions() {
			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return ForgeTabletRenderer.get();
			}
		});
	}
}
