package ds.mods.CCLights2;

import java.io.File;
import java.util.List;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import dan200.computercraft.api.ComputerCraftAPI;
import ds.mods.CCLights2.block.BlockExternalMonitor;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.BlockTabletTransceiver;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.item.ItemTablet;
import ds.mods.CCLights2.utils.ServerTaskQueue;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class CommonProxy {
	public static int modelID;

	private final ServerTaskQueue serverTasks = new ServerTaskQueue();

	public void registerBlocks() {
		ComputerCraftAPI.registerPeripheralProvider(new PeripheralProvider());

		CCLights2.gpu = new BlockGPU(Material.iron);
		GameRegistry.registerBlock(CCLights2.gpu, "CCLGPU");
		GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");

		CCLights2.monitor = new BlockMonitor(Material.iron);
		GameRegistry.registerBlock(CCLights2.monitor, "CCLMonitor");
		GameRegistry.registerTileEntity(TileEntityMonitor.class, "CCLMonitorTE");

		CCLights2.monitorBig = new BlockExternalMonitor(Material.iron);
		GameRegistry.registerBlock(CCLights2.monitorBig, "CCLBigMonitor");
		GameRegistry.registerTileEntity(TileEntityExternalMonitor.class, "CCLBigMonitorTE");

		CCLights2.ttrans = new BlockTabletTransceiver(Material.iron);
		GameRegistry.registerBlock(CCLights2.ttrans, "CCLTTrans");
		GameRegistry.registerTileEntity(TileEntityTTrans.class, "CCLTTransTE");

		CCLights2.ram = new ItemRAM();
		GameRegistry.registerItem(CCLights2.ram, "CCLRAM");

		CCLights2.tablet = new ItemTablet();
		GameRegistry.registerItem(CCLights2.tablet, "CCLTab");

		if (Config.vanillaRecipes) registerVanillaRecipes();
	}

	private void registerVanillaRecipes() {
		GameRegistry.addRecipe(new ItemStack(CCLights2.gpu, 1), "III", "RGR", "GGG",
				'I', Items.iron_ingot, 'R', Items.redstone, 'G', Items.gold_ingot);
		GameRegistry.addRecipe(new ItemStack(CCLights2.monitor, 2), "III", "RLR", "GGG",
				'I', Items.iron_ingot, 'R', Items.redstone, 'G', Items.gold_ingot, 'L', Blocks.glass_pane);
		GameRegistry.addRecipe(new ItemStack(CCLights2.monitorBig, 8), "LLL", "LGL", "LLL",
				'G', CCLights2.monitor, 'L', Blocks.glass_pane);
		GameRegistry.addRecipe(new ItemStack(CCLights2.ttrans, 1), " L ", "LGL", " L ",
				'G', CCLights2.monitor, 'L', Items.redstone);
		GameRegistry.addRecipe(new ItemStack(CCLights2.ram, 8), "III", "R R", "GGG",
				'I', Items.iron_ingot, 'R', Blocks.redstone_block, 'G', Items.gold_ingot);
		// Combine two RAM sticks into a bigger one (damage value + 1 = size in K).
		for (int a = 0; a < ItemRAM.SIZES; a++) {
			for (int b = a; b < ItemRAM.SIZES; b++) {
				int total = (a + 1) + (b + 1);
				if (total <= ItemRAM.SIZES) {
					GameRegistry.addShapelessRecipe(new ItemStack(CCLights2.ram, 1, total - 1), new ItemStack(CCLights2.ram, 1, a), new ItemStack(CCLights2.ram, 1, b));
				}
			}
		}
		GameRegistry.addRecipe(new ItemStack(CCLights2.tablet, 1), "GIG", "RMR", "GIG",
				'I', Items.iron_ingot, 'R', Items.redstone, 'G', Items.gold_ingot, 'M', CCLights2.monitorBig);
	}

	public void registerHandlers() {
		FMLCommonHandler.instance().bus().register(serverTasks);
	}

	public void registerRenderInfo() {}

	/** Runs a task on the game thread of the given side. */
	public void runOnGameThread(Side side, Runnable task) {
		serverTasks.submit(task);
	}

	/** Client only: hands replicated draw commands to the draw thread. */
	public void submitDraw(GPU gpu, List<DrawCMD> cmds) {}

	/** Client only. */
	public World getClientWorld() {
		return null;
	}

	/** Directory holding the world save; used to resolve gpu.import("file") relative to the computer folder. */
	public File getWorldDir(World world) {
		return new File(FMLCommonHandler.instance().getMinecraftServerInstance().getFile("."), DimensionManager.getWorld(0).getSaveHandler().getWorldDirectoryName());
	}
}
