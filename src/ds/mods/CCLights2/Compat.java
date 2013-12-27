package ds.mods.CCLights2;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import ds.mods.CCLights2.block.BlockAdvancedLight;
import ds.mods.CCLights2.block.BlockBigMonitor;
import ds.mods.CCLights2.block.BlockColorLight;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.BlockTabletTransceiver;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.item.ItemTablet;

public class Compat {
	
	public static void Vanilla(){
		LanguageRegistry.instance().addStringLocalization("itemGroup.CClights2", "en_US", "CCLights 2");
	    // gpu
		NetworkRegistry.instance().registerGuiHandler(CCLights2.class, new GuiHandler());
		CCLights2.gpu = new BlockGPU(Config.Gpu, Material.iron);
		GameRegistry.registerBlock(CCLights2.gpu, "CCLGPU");
		LanguageRegistry.addName(CCLights2.gpu, "GPU");
		GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");
		GameRegistry.addRecipe(new ItemStack(CCLights2.gpu, 1), new Object[] { "III",
				"RGR", "GGG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',
				Item.ingotGold });
		CCLights2.monitor = new BlockMonitor(Config.Monitor, Material.iron);
		// monitor
		GameRegistry.registerBlock(CCLights2.monitor, "CCLMonitor");
		LanguageRegistry.addName(CCLights2.monitor, "Monitor");
		GameRegistry.registerTileEntity(TileEntityMonitor.class, "CCLMonitorTE");
		GameRegistry.addRecipe(new ItemStack(CCLights2.monitor, 2), new Object[] { "III",
				"RLR", "GGG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',
				Item.ingotGold, 'L', Block.thinGlass });
		// big monitor
		CCLights2.monitorBig = new BlockBigMonitor(Config.MonitorBig, Material.iron);
		GameRegistry.registerBlock(CCLights2.monitorBig, "CCLBigMonitor");
		LanguageRegistry.addName(CCLights2.monitorBig, "External Monitor");
		GameRegistry.registerTileEntity(TileEntityBigMonitor.class,
				"CCLBigMonitorTE");
		GameRegistry.addRecipe(new ItemStack(CCLights2.monitorBig, 8), new Object[] {
				"LLL", "LGL", "LLL", 'G', CCLights2.monitor, 'L', Block.thinGlass });
		//lightblock normal
		CCLights2.light = new BlockColorLight(Config.light, Material.iron);
		GameRegistry.registerBlock(CCLights2.light, "CCLLIGHT");
		LanguageRegistry.addName(CCLights2.light, "Light");
		
		//lightblock advanced
		CCLights2.advancedlight = new BlockAdvancedLight(Config.advlight, Material.iron);
		GameRegistry.registerBlock(CCLights2.advancedlight, "CCLADVLIGHT");
		LanguageRegistry.addName(CCLights2.advancedlight, "Advanced Light");

		// tablet trans
		CCLights2.ttrans = new BlockTabletTransceiver(Config.TTrans, Material.iron);
		GameRegistry.registerBlock(CCLights2.ttrans, "CCLTTrans");
		LanguageRegistry.addName(CCLights2.ttrans, "Tablet Transmitter");
		GameRegistry.registerTileEntity(TileEntityTTrans.class, "CCLTTransTE");
		GameRegistry.addRecipe(new ItemStack(CCLights2.ttrans, 1), new Object[] { " L ",
				"LGL", " L ", 'G', CCLights2.monitor, 'L', Item.redstone });

		// RAM
		CCLights2.ram = new ItemRAM(Config.Ram);
		GameRegistry.registerItem(CCLights2.ram, "CCLRAM");
		LanguageRegistry.addName(CCLights2.ram, "Random Access Memory");
		GameRegistry.addRecipe(new ItemStack(CCLights2.ram, 8), new Object[] { "III",
				"R R", "GGG", 'I', Item.ingotIron, 'R', Block.blockRedstone,
				'G', Item.ingotGold, 'L', Block.thinGlass });
		// register recipes for RAM upgrades   item,output,metadata
		for (int i = 0; i<8; i++){
			for (int x = 0; x<8; x++){
				int total = i+x;
				if (total <= 8 && i != total && x != total){
				CCLights2.debug(i+"+"+x+"="+total);
				GameRegistry.addShapelessRecipe(new ItemStack(CCLights2.ram, 1, total+1), new ItemStack(CCLights2.ram, 1, i), new ItemStack(CCLights2.ram, 1, x));
				}
			}
		}
		// Tablet
		CCLights2.tablet = new ItemTablet(Config.Tablet);
		GameRegistry.registerItem(CCLights2.tablet, "CCLTab");
		LanguageRegistry.addName(CCLights2.tablet, "Tablet");
		GameRegistry.addRecipe(new ItemStack(CCLights2.tablet, 2), new Object[] { "GIG",
				"RMR", "GIG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',
				Item.ingotGold, 'M', CCLights2.monitorBig });
	}
	
	public static void IC2(){
		// do some stuff to fak over recipes here kthxbai
	}

}
