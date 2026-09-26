package ds.mods.CCLights2.item;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Registration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A RAM stick (ram_1k, ram_2k, ram_4k, ram_8k; port of ItemRAM, whose damage value became four items).
 * Right clicking a GPU installs it (GpuBlockEntity#onUse): the GPU gains
 * {@code kilobytes * Config.gpuRamPerStick} texture memory.
 */
public class RamItem extends Item {
	/** Sizes of the four RAM items, in the order GpuBlockEntity stores its installed counts. */
	public static final int[] SIZES = { 1, 2, 4, 8 };

	private final int kilobytes;

	public RamItem(int kilobytes) {
		super(new Item.Properties());
		this.kilobytes = kilobytes;
	}

	public int getKilobytes() {
		return kilobytes;
	}

	/** Texture memory this stick adds to a GPU. */
	public int getMemory() {
		return kilobytes * Config.gpuRamPerStick;
	}

	/** Index of a size in {@link #SIZES}, or -1. */
	public static int sizeIndex(int kilobytes) {
		for (int i = 0; i < SIZES.length; i++) if (SIZES[i] == kilobytes) return i;
		return -1;
	}

	/** The registered RAM item for an index into {@link #SIZES}, or null. */
	@Nullable
	public static RamItem bySizeIndex(int index) {
		Supplier<RamItem> s = switch (index) {
		case 0 -> Registration.RAM_1K;
		case 1 -> Registration.RAM_2K;
		case 2 -> Registration.RAM_4K;
		case 3 -> Registration.RAM_8K;
		default -> null;
		};
		return s == null ? null : s.get();
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("tooltip.cclights.ram.size", kilobytes, getMemory()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.cclights.ram.install").withStyle(ChatFormatting.GRAY));
	}
}
