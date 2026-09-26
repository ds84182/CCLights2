package ds.mods.CCLights2.fabric;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.network.NetworkKinds;
import ds.mods.CCLights2.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fabric implementation of {@link Platform}; registered in META-INF/services. */
public class FabricPlatform implements Platform {
	private static ResourceLocation id(String name) {
		return new ResourceLocation(CCLights2.MOD_ID, name);
	}

	@Override
	public Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isClient() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
	}

	@Override
	public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block) {
		T b = Registry.register(BuiltInRegistries.BLOCK, id(name), block.get());
		Registry.register(BuiltInRegistries.ITEM, id(name), new BlockItem(b, new Item.Properties()));
		return () -> b;
	}

	@Override
	public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
		T i = Registry.register(BuiltInRegistries.ITEM, id(name), item.get());
		return () -> i;
	}

	@SafeVarargs
	@Override
	public final <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntity(String name, BlockEntityFactory<T> factory, Supplier<? extends Block>... blocks) {
		Block[] resolved = new Block[blocks.length];
		for (int i = 0; i < blocks.length; i++) resolved[i] = blocks[i].get();
		BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(name),
				FabricBlockEntityTypeBuilder.create(factory::create, resolved).build());
		return () -> type;
	}

	private static FriendlyByteBuf encode(int kind, byte[] payload) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(kind);
		buf.writeByteArray(payload);
		return buf;
	}

	@Override
	public void sendToServer(int kind, byte[] payload) {
		FabricClientHooks.send(kind, payload);
	}

	@Override
	public void sendToPlayer(ServerPlayer player, int kind, byte[] payload) {
		ServerPlayNetworking.send(player, NetworkKinds.CHANNEL, encode(kind, payload));
	}

	@Override
	public void sendToTracking(BlockEntity be, int kind, byte[] payload) {
		for (ServerPlayer player : PlayerLookup.tracking(be)) {
			ServerPlayNetworking.send(player, NetworkKinds.CHANNEL, encode(kind, payload));
		}
	}

	@Override
	public void registerNetworkReceivers(NetworkReceiver server, NetworkReceiver client) {
		ServerPlayNetworking.registerGlobalReceiver(NetworkKinds.CHANNEL, (srv, player, handler, buf, responseSender) -> {
			int kind = buf.readVarInt();
			byte[] payload = buf.readByteArray();
			srv.execute(() -> server.receive(kind, payload, player));
		});
		if (isClient()) FabricClientHooks.registerReceiver(client);
	}

	@Override
	public void registerPeripheralProviders() {
		// One cached peripheral per block entity, exposed on every side (wired modems included).
		PeripheralLookup.get().registerForBlockEntity((be, side) -> be.peripheral(), Registration.GPU_BE.get());
		PeripheralLookup.get().registerForBlockEntity((be, side) -> be.peripheral(), Registration.TABLET_TRANSCEIVER_BE.get());
	}

	@Override
	public void registerCreativeTab(String name, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items) {
		String title = name.equals(CCLights2.MOD_ID) ? "itemGroup." + CCLights2.MOD_ID : "itemGroup." + CCLights2.MOD_ID + "." + name;
		CreativeModeTab tab = FabricItemGroup.builder()
				.title(Component.translatable(title))
				.icon(icon)
				.displayItems((params, output) -> output.acceptAll(items.get()))
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id(name), tab);
	}

	@Override
	public void registerClientTick(Runnable r) {
		FabricClientHooks.registerClientTick(r);
	}
}
