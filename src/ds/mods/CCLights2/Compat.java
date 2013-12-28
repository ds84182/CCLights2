package ds.mods.CCLights2;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import ds.mods.CCLights2.block.BlockAdvancedLight;
import ds.mods.CCLights2.block.BlockExternalMonitor;
import ds.mods.CCLights2.block.BlockColorLight;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.BlockTabletTransceiver;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityColorLight;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.item.ItemTablet;

public class Compat {
	
	public static void init(){
		LanguageRegistry.instance().addStringLocalization("itemGroup.CClights2", "en_US", "CCLights 2"); //creativetabs
		
		NetworkRegistry.instance().registerGuiHandler(CCLights2.class, new GuiHandler()); //gpu
        CCLights2.gpu = new BlockGPU(Config.Gpu, Material.iron);
		GameRegistry.registerBlock(CCLights2.gpu, "CCLGPU");
		LanguageRegistry.addName(CCLights2.gpu, "GPU");
		GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");
		
		CCLights2.monitor = new BlockMonitor(Config.Monitor, Material.iron);//Monitor
		GameRegistry.registerBlock(CCLights2.monitor, "CCLMonitor");
		LanguageRegistry.addName(CCLights2.monitor, "Monitor");
		GameRegistry.registerTileEntity(TileEntityMonitor.class, "CCLMonitorTE");
		
		CCLights2.monitorBig = new BlockExternalMonitor(Config.MonitorBig, Material.iron); // big Monitor (screen dammit)
		GameRegistry.registerBlock(CCLights2.monitorBig, "CCLBigMonitor");
		LanguageRegistry.addName(CCLights2.monitorBig, "External Monitor");
		GameRegistry.registerTileEntity(TileEntityExternalMonitor.class,"CCLBigMonitorTE");
        
		CCLights2.light = new BlockColorLight(Config.light, Material.iron); //lightblock normal
		GameRegistry.registerBlock(CCLights2.light, "CCLLIGHT");
		LanguageRegistry.addName(CCLights2.light, "Light");
		GameRegistry.registerTileEntity(TileEntityColorLight.class, "CCLLight");
		
		CCLights2.advancedlight = new BlockAdvancedLight(Config.advlight, Material.iron); //lightblock advanced
		GameRegistry.registerBlock(CCLights2.advancedlight, "CCLADVLIGHT");
		LanguageRegistry.addName(CCLights2.advancedlight, "Advanced Light");
		
		CCLights2.ttrans = new BlockTabletTransceiver(Config.TTrans, Material.iron); // tablet transfer block
		GameRegistry.registerBlock(CCLights2.ttrans, "CCLTTrans");
		LanguageRegistry.addName(CCLights2.ttrans, "Tablet Transmitter");
		GameRegistry.registerTileEntity(TileEntityTTrans.class, "CCLTTransTE");
		
		CCLights2.ram = new ItemRAM(Config.Ram); //ram
		GameRegistry.registerItem(CCLights2.ram, "CCLRAM");
		LanguageRegistry.addName(CCLights2.ram, "Random Access Memory");
		
		CCLights2.tablet = new ItemTablet(Config.Tablet); // tablet
		GameRegistry.registerItem(CCLights2.tablet, "CCLTab");
		LanguageRegistry.addName(CCLights2.tablet, "Tablet");
	}
	
	public static void Vanilla(){
		GameRegistry.addRecipe(new ItemStack(CCLights2.gpu, 1), new Object[] { "III",
			    "RGR", "GGG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',Item.ingotGold }); // gpu
		GameRegistry.addRecipe(new ItemStack(CCLights2.monitor, 2), new Object[] { "III",
				"RLR", "GGG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',Item.ingotGold, 'L', Block.thinGlass }); // Monitor
		GameRegistry.addRecipe(new ItemStack(CCLights2.monitorBig, 8), new Object[] {
				"LLL", "LGL", "LLL", 'G', CCLights2.monitor, 'L', Block.thinGlass }); // big Monitor
		GameRegistry.addRecipe(new ItemStack(CCLights2.ttrans, 1), new Object[] { " L ",
				"LGL", " L ", 'G', CCLights2.monitor, 'L', Item.redstone }); // tablet transciever
		GameRegistry.addRecipe(new ItemStack(CCLights2.ram, 8), new Object[] { "III",
				"R R", "GGG", 'I', Item.ingotIron, 'R', Block.blockRedstone,'G', Item.ingotGold, 'L', Block.thinGlass });
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
		GameRegistry.addRecipe(new ItemStack(CCLights2.tablet, 2), new Object[] { "GIG",
				"RMR", "GIG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',Item.ingotGold, 'M', CCLights2.monitorBig });
	}
	
	public static void IC2(){
		// do some stuff to fak over recipes here kthxbai
	}

}
