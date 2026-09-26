package ds.mods.CCLights2;

import java.util.List;
import java.util.function.Supplier;

import ds.mods.CCLights2.block.ExternalMonitorBlock;
import ds.mods.CCLights2.block.GpuBlock;
import ds.mods.CCLights2.block.MonitorBlock;
import ds.mods.CCLights2.block.TabletTransceiverBlock;
import ds.mods.CCLights2.block.entity.ExternalMonitorBlockEntity;
import ds.mods.CCLights2.block.entity.GpuBlockEntity;
import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.item.RamItem;
import ds.mods.CCLights2.item.TabletItem;
import ds.mods.CCLights2.platform.Platform;
import ds.mods.CCLights2.platform.Services;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * All blocks, items and block entity types. The holders are filled by {@link #register()}, which
 * {@link CCLights2#init()} calls once; call {@code .get()} only after the loader has run registration.
 */
public final class Registration {
	public static Supplier<GpuBlock> GPU_BLOCK;
	public static Supplier<MonitorBlock> MONITOR_BLOCK;
	public static Supplier<ExternalMonitorBlock> EXTERNAL_MONITOR_BLOCK;
	public static Supplier<TabletTransceiverBlock> TABLET_TRANSCEIVER_BLOCK;

	public static Supplier<TabletItem> TABLET_ITEM;
	public static Supplier<RamItem> RAM_1K;
	public static Supplier<RamItem> RAM_2K;
	public static Supplier<RamItem> RAM_4K;
	public static Supplier<RamItem> RAM_8K;

	public static Supplier<BlockEntityType<GpuBlockEntity>> GPU_BE;
	public static Supplier<BlockEntityType<MonitorBlockEntity>> MONITOR_BE;
	public static Supplier<BlockEntityType<ExternalMonitorBlockEntity>> EXTERNAL_MONITOR_BE;
	public static Supplier<BlockEntityType<TabletTransceiverBlockEntity>> TABLET_TRANSCEIVER_BE;

	private Registration() {}

	private static BlockBehaviour.Properties machine() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL);
	}

	@SuppressWarnings("unchecked")
	public static void register() {
		Platform p = Services.PLATFORM;

		GPU_BLOCK = p.registerBlock("gpu", () -> new GpuBlock(machine()));
		MONITOR_BLOCK = p.registerBlock("monitor", () -> new MonitorBlock(machine()));
		EXTERNAL_MONITOR_BLOCK = p.registerBlock("external_monitor", () -> new ExternalMonitorBlock(machine()));
		TABLET_TRANSCEIVER_BLOCK = p.registerBlock("tablet_transceiver", () -> new TabletTransceiverBlock(machine()));

		TABLET_ITEM = p.registerItem("tablet", p::createTabletItem);
		RAM_1K = p.registerItem("ram_1k", () -> new RamItem(1));
		RAM_2K = p.registerItem("ram_2k", () -> new RamItem(2));
		RAM_4K = p.registerItem("ram_4k", () -> new RamItem(4));
		RAM_8K = p.registerItem("ram_8k", () -> new RamItem(8));

		GPU_BE = p.registerBlockEntity("gpu", GpuBlockEntity::new, GPU_BLOCK);
		MONITOR_BE = p.registerBlockEntity("monitor", MonitorBlockEntity::new, MONITOR_BLOCK);
		EXTERNAL_MONITOR_BE = p.registerBlockEntity("external_monitor", ExternalMonitorBlockEntity::new, EXTERNAL_MONITOR_BLOCK);
		TABLET_TRANSCEIVER_BE = p.registerBlockEntity("tablet_transceiver", TabletTransceiverBlockEntity::new, TABLET_TRANSCEIVER_BLOCK);

		p.registerCreativeTab(CCLights2.MOD_ID, () -> new ItemStack(GPU_BLOCK.get()), Registration::creativeTabItems);
	}

	/** Everything the mod adds, in creative tab order. */
	public static List<ItemStack> creativeTabItems() {
		return List.of(
				new ItemStack(GPU_BLOCK.get()),
				new ItemStack(MONITOR_BLOCK.get()),
				new ItemStack(EXTERNAL_MONITOR_BLOCK.get()),
				new ItemStack(TABLET_TRANSCEIVER_BLOCK.get()),
				new ItemStack(TABLET_ITEM.get()),
				new ItemStack(RAM_1K.get()),
				new ItemStack(RAM_2K.get()),
				new ItemStack(RAM_4K.get()),
				new ItemStack(RAM_8K.get()));
	}
}
