package ds.mods.CCLights2;

import java.io.File;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.api.ComputerCraftAPI;
import ds.mods.CCLights2.block.BlockExternalMonitor;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.BlockTabletTransceiver;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.item.ItemTablet;
import ds.mods.CCLights2.utils.RegisterHelper;

public class CommonProxy {
	public static int modelID;
	
	public  void registerRenderInfo(){};
	
	public void registerBlocks()
	{	
		boolean gpu = false, monitor = false, monitorBig = false, light = false, advancedlight = false, ttrans = false, ram = false, tablet = false;
		if(RegisterHelper.canRegisterBlock(Config.Gpu, "GPU")) {
			CCLights2.gpu = new BlockGPU(Config.Gpu, Material.iron);
			
			GameRegistry.registerBlock(CCLights2.gpu, "CCLGPU");
			GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");
			gpu = true;
		}
		ComputerCraftAPI.registerPeripheralProvider(new PeripheralpProvider());
		if (RegisterHelper.canRegisterBlock(Config.Monitor, "Monitor")) {
			CCLights2.monitor = new BlockMonitor(Config.Monitor, Material.iron);
			
			GameRegistry.registerBlock(CCLights2.monitor, "CCLMonitor");
			GameRegistry.registerTileEntity(TileEntityMonitor.class, "CCLMonitorTE");
			
			monitor = true;
		}
		
		if (RegisterHelper.canRegisterBlock(Config.MonitorBig, "ExternalMonitor")) {
			CCLights2.monitorBig = new BlockExternalMonitor(Config.MonitorBig, Material.iron);
			
			GameRegistry.registerBlock(CCLights2.monitorBig, "CCLBigMonitor");
			GameRegistry.registerTileEntity(TileEntityExternalMonitor.class, "CCLBigMonitorTE");
			
			monitorBig = true;
		}
		
		/*if (RegisterHelper.canRegisterBlock(Config.light, "Light")) {
			CCLights2.light = new BlockColorLight(Config.light, Material.iron);
																				
			GameRegistry.registerBlock(CCLights2.light, "CCLLIGHT");
			GameRegistry.registerTileEntity(TileEntityColorLight.class, "CCLLight");
			light = true;
		}

		if (RegisterHelper.canRegisterBlock(Config.advlight, "advLight")) {
			CCLights2.advancedlight = new BlockAdvancedLight(Config.advlight, Material.iron);
			
			GameRegistry.registerBlock(CCLights2.advancedlight, "CCLADVLIGHT");
			GameRegistry.registerTileEntity(TileEntityAdvancedlight.class, "CCLAdvLight");
			
			advancedlight = true;
		}*/

		if (RegisterHelper.canRegisterBlock(Config.TTrans, "TableTransciever")) {
			CCLights2.ttrans = new BlockTabletTransceiver(Config.TTrans, Material.iron);
			
			GameRegistry.registerBlock(CCLights2.ttrans, "CCLTTrans");
			GameRegistry.registerTileEntity(TileEntityTTrans.class, "CCLTTransTE");
			
			ttrans = true;
		}

		if (RegisterHelper.canRegisterItem(Config.Ram, "Ram")) {
			CCLights2.ram = new ItemRAM(Config.Ram);
			
			GameRegistry.registerItem(CCLights2.ram, "CCLRAM");
			
			ram = true;
		}

		if (RegisterHelper.canRegisterItem(Config.Tablet, "Tablet")) {
			CCLights2.tablet = new ItemTablet(Config.Tablet);
			
			GameRegistry.registerItem(CCLights2.tablet, "CCLTab");
			
			tablet = true;
		}
		
		if (Config.Vanilla) {
			registerVanillaRecipes(gpu, monitor, monitorBig, light, advancedlight, ttrans, ram, tablet);
		}
		if (Loader.isModLoaded("IC2") && Config.IC2) {
			registerIC2Recipes(gpu, monitor, monitorBig, light, advancedlight, ttrans, ram, tablet);
		}
	}
	
	private void registerVanillaRecipes(boolean gpu, boolean monitor, boolean monitorBig, boolean light, boolean advancedlight,
			boolean ttrans, boolean ram, boolean tablet) {

		if (gpu) {
			GameRegistry.addRecipe(new ItemStack(CCLights2.gpu, 1),
					new Object[] { "III", "RGR", "GGG", 'I',
							Item.ingotIron, 'R', Item.redstone, 'G',
							Item.ingotGold });
		}
		if (monitor) {
			GameRegistry.addRecipe(new ItemStack(CCLights2.monitor, 2),
					new Object[] { "III", "RLR", "GGG", 'I',
							Item.ingotIron, 'R', Item.redstone, 'G',
							Item.ingotGold, 'L', Block.thinGlass });
		}
		if (monitorBig) {
			GameRegistry.addRecipe(new ItemStack(CCLights2.monitorBig, 8),
					new Object[] { "LLL", "LGL", "LLL", 'G',
							CCLights2.monitor, 'L', Block.thinGlass });
		}
		if (ttrans) {
			GameRegistry.addRecipe(new ItemStack(CCLights2.ttrans, 1),
					new Object[] { " L ", "LGL", " L ", 'G',
							CCLights2.monitor, 'L', Item.redstone });
		}
		if (ram) {
			GameRegistry.addRecipe(new ItemStack(CCLights2.ram, 8),
					new Object[] { "III", "R R", "GGG", 'I', Item.ingotIron, 'R', Block.blockRedstone, 'G', Item.ingotGold, 'L', Block.thinGlass });
			
			// register recipes for RAM upgrades item,output,metadata
			for (int i = 0; i < 8; i++) {
				for (int x = 0; x < 8; x++) {
					int total = i + x;
					if (total <= 8 && i != total && x != total) {
						GameRegistry.addShapelessRecipe(new ItemStack( CCLights2.ram, 1, total + 1), new ItemStack(CCLights2.ram, 1, i),
								new ItemStack(CCLights2.ram, 1, x));
					}
				}
			}
		}
		if (tablet) {
			GameRegistry.addRecipe(new ItemStack(CCLights2.tablet, 2),
					new Object[] { "GIG", "RMR", "GIG", 'I',
							Item.ingotIron, 'R', Item.redstone, 'G',
							Item.ingotGold, 'M', CCLights2.monitorBig });
		}
	}
	
	public void registerIC2Recipes(boolean gpu, boolean monitor,boolean monitorBig, boolean light, boolean advancedlight,boolean ttrans, boolean ram, boolean tablet) {
		// do some stuff to fak over recipes here kthxbai
	}
	
	public File getWorldDir(World world)
	  {
	    return new File(FMLCommonHandler.instance().getMinecraftServerInstance().getFile("."), DimensionManager.getWorld(0).getSaveHandler().getWorldDirectoryName());
	  }
}
