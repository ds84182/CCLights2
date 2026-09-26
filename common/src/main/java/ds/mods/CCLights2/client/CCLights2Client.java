package ds.mods.CCLights2.client;

import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.block.entity.ExternalMonitorBlockEntity;
import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.client.render.ExternalMonitorRenderer;
import ds.mods.CCLights2.client.render.ScreenFaceRenderer;
import ds.mods.CCLights2.network.ClientPacketHandlers;
import ds.mods.CCLights2.platform.Services;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Client setup, called from CCLights2.initClient() on the physical client only. */
public final class CCLights2Client {
	private CCLights2Client() {}

	public static void init() {
		Services.PLATFORM.registerClientTick(ClientTickHandler::onClientTick);
		// Block entity renderers: vanilla BlockEntityRenderers.register is private in the common
		// (unwidened) jar, so each loader passes its registrar to registerRenderers(...):
		// Fabric from CCLights2FabricClient, Forge from ForgeClientHooks (RegisterRenderers event).
	}

	/**
	 * Sink for block entity renderer registrations: {@code BlockEntityRenderers::register} (Fabric, widened
	 * by Fabric API) or {@code EntityRenderersEvent.RegisterRenderers::registerBlockEntityRenderer} (Forge).
	 */
	@FunctionalInterface
	public interface RendererRegistrar {
		<T extends BlockEntity> void register(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider);
	}

	/** Registers the screen renderers for the external monitor wall, the monitor and the transceiver. */
	public static void registerRenderers(RendererRegistrar registrar) {
		registrar.<ExternalMonitorBlockEntity>register(Registration.EXTERNAL_MONITOR_BE.get(), ExternalMonitorRenderer::new);
		registrar.<MonitorBlockEntity>register(Registration.MONITOR_BE.get(), ScreenFaceRenderer<MonitorBlockEntity>::new);
		registrar.<TabletTransceiverBlockEntity>register(Registration.TABLET_TRANSCEIVER_BE.get(), ScreenFaceRenderer<TabletTransceiverBlockEntity>::new);
	}

	/**
	 * The client level changed (joined, switched dimension or left): frees the screen textures, and when
	 * the world was left, the queued draw work and partial messages. A dimension switch keeps the
	 * message state, since packets for the new dimension may already be half received.
	 */
	public static void onLevelChanged(boolean leftWorld) {
		if (leftWorld) ClientPacketHandlers.clear();
		TextureCache.releaseAll();
	}
}
