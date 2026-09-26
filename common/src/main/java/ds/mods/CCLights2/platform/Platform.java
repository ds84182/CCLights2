package ds.mods.CCLights2.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything that differs between mod loaders. One implementation per loader module
 * ({@code ds.mods.CCLights2.fabric.FabricPlatform}, {@code ds.mods.CCLights2.forge.ForgePlatform}),
 * found through {@link Services#PLATFORM} (java.util.ServiceLoader, META-INF/services).
 */
public interface Platform {
	/** The loader's config directory (config/ in the game directory). */
	Path getConfigDir();

	/** True on the physical client (integrated server included), false on a dedicated server. */
	boolean isClient();

	/**
	 * Registers a block as {@code cclights:<name>} plus a plain BlockItem with the same name.
	 * The returned supplier is valid once the loader has processed registrations.
	 */
	<T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block);

	/** Registers an item as {@code cclights:<name>}. */
	<T extends Item> Supplier<T> registerItem(String name, Supplier<T> item);

	/**
	 * The tablet item instance. Loaders that attach an in-hand renderer through a method on the item
	 * (Forge's initializeClient) return a subclass; the renderer itself is the common
	 * client.render.TabletItemRenderer.
	 */
	default ds.mods.CCLights2.item.TabletItem createTabletItem() {
		return new ds.mods.CCLights2.item.TabletItem();
	}

	/** Registers a block entity type valid for the given blocks. */
	@SuppressWarnings("unchecked")
	<T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntity(String name, BlockEntityFactory<T> factory, Supplier<? extends Block>... blocks);

	/** Client to server. Serverbound payloads must stay below 32 KB; use PacketChunker for more. */
	void sendToServer(int kind, byte[] payload);

	/** Server to one client. */
	void sendToPlayer(ServerPlayer player, int kind, byte[] payload);

	/** Server to every client tracking the chunk the block entity is in. */
	void sendToTracking(BlockEntity be, int kind, byte[] payload);

	/**
	 * Installs the handlers for the single {@code cclights:msg} channel. Both run on the main thread
	 * (server thread / client thread). {@code client} is only installed on the physical client.
	 */
	void registerNetworkReceivers(NetworkReceiver server, NetworkReceiver client);

	/** Exposes the GPU and tablet transceiver block entities to CC: Tweaked (capability / PeripheralLookup). */
	void registerPeripheralProviders();

	/**
	 * Registers a creative mode tab {@code cclights:<name>} titled {@code itemGroup.cclights} (when
	 * {@code name} is the mod id) or {@code itemGroup.cclights.<name>}. The suppliers are called when
	 * the tab is built, after registration.
	 */
	void registerCreativeTab(String name, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items);

	/** Runs {@code r} at the end of every client tick. Client only. */
	void registerClientTick(Runnable r);

	/** Same shape as vanilla BlockEntityType.BlockEntitySupplier, which is not public on every loader. */
	@FunctionalInterface
	interface BlockEntityFactory<T extends BlockEntity> {
		T create(BlockPos pos, BlockState state);
	}

	/** Receives one {@code (kind, payload)} message on the main thread. */
	@FunctionalInterface
	interface NetworkReceiver {
		/**
		 * @param player the sender on the server side; null when the client receives from the server
		 */
		void receive(int kind, byte[] payload, @Nullable ServerPlayer player);
	}
}
