package ds.mods.CCLights2.forge;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import dan200.computercraft.api.ForgeComputerCraftAPI;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.entity.GpuBlockEntity;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.network.NetworkKinds;
import ds.mods.CCLights2.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge implementation of {@link Platform}; registered in META-INF/services. */
public class ForgePlatform implements Platform {
	static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CCLights2.MOD_ID);
	static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CCLights2.MOD_ID);
	static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CCLights2.MOD_ID);
	/** Attached to the mod bus by the first registerCreativeTab call (during mod construction). */
	static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CCLights2.MOD_ID);
	private static boolean creativeTabsRegistered = false;

	private static final String PROTOCOL = "1";
	static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NetworkKinds.CHANNEL, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	/** The single message type on the channel. */
	record Message(int kind, byte[] payload) {
		void encode(FriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			buf.writeByteArray(payload);
		}

		static Message decode(FriendlyByteBuf buf) {
			int kind = buf.readVarInt();
			return new Message(kind, buf.readByteArray());
		}
	}

	@Override
	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isClient() {
		return FMLEnvironment.dist.isClient();
	}

	@Override
	public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block) {
		RegistryObject<T> obj = BLOCKS.register(name, block);
		ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
		return obj;
	}

	@Override
	public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
		return ITEMS.register(name, item);
	}

	@Override
	public ds.mods.CCLights2.item.TabletItem createTabletItem() {
		return new ForgeTabletItem();
	}

	@SafeVarargs
	@Override
	public final <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntity(String name, BlockEntityFactory<T> factory, Supplier<? extends Block>... blocks) {
		return BLOCK_ENTITY_TYPES.register(name, () -> {
			Block[] resolved = new Block[blocks.length];
			for (int i = 0; i < blocks.length; i++) resolved[i] = blocks[i].get();
			return BlockEntityType.Builder.of(factory::create, resolved).build(null);
		});
	}

	@Override
	public void sendToServer(int kind, byte[] payload) {
		CHANNEL.sendToServer(new Message(kind, payload));
	}

	@Override
	public void sendToPlayer(ServerPlayer player, int kind, byte[] payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Message(kind, payload));
	}

	@Override
	public void sendToTracking(BlockEntity be, int kind, byte[] payload) {
		if (be.getLevel() == null) return;
		CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> be.getLevel().getChunkAt(be.getBlockPos())), new Message(kind, payload));
	}

	@Override
	public void registerNetworkReceivers(NetworkReceiver server, NetworkReceiver client) {
		CHANNEL.registerMessage(0, Message.class, Message::encode, Message::decode, (msg, ctxSupplier) -> {
			NetworkEvent.Context ctx = ctxSupplier.get();
			if (ctx.getDirection().getReceptionSide().isServer()) {
				ServerPlayer sender = ctx.getSender();
				ctx.enqueueWork(() -> server.receive(msg.kind(), msg.payload(), sender));
			} else {
				ctx.enqueueWork(() -> client.receive(msg.kind(), msg.payload(), null));
			}
			ctx.setPacketHandled(true);
		});
	}

	@Override
	@SuppressWarnings("removal") // FMLJavaModLoadingContext.get(): see CCLights2Forge
	public void registerPeripheralProviders() {
		// Registered in common setup, once CC: Tweaked's API service is up. Each block entity hands out its one
		// cached peripheral, on every side (wired modems included).
		FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, event -> event.enqueueWork(() ->
				ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) -> {
					BlockEntity be = level.getBlockEntity(pos);
					if (be instanceof GpuBlockEntity gpu) return LazyOptional.of(gpu::peripheral);
					if (be instanceof TabletTransceiverBlockEntity transceiver) return LazyOptional.of(transceiver::peripheral);
					return LazyOptional.empty();
				})));
	}

	@Override
	@SuppressWarnings("removal") // FMLJavaModLoadingContext.get(): see CCLights2Forge
	public void registerCreativeTab(String name, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items) {
		if (!creativeTabsRegistered) {
			creativeTabsRegistered = true;
			CREATIVE_TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
		}
		String title = name.equals(CCLights2.MOD_ID) ? "itemGroup." + CCLights2.MOD_ID : "itemGroup." + CCLights2.MOD_ID + "." + name;
		CREATIVE_TABS.register(name, () -> CreativeModeTab.builder()
				.title(Component.translatable(title))
				.icon(icon)
				.displayItems((params, output) -> output.acceptAll(items.get()))
				.build());
	}

	@Override
	public void registerClientTick(Runnable r) {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) r.run();
		});
	}
}
