package ds.mods.CCLights2;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import ds.mods.CCLights2.block.BlockBigMonitor;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.BlockTabletTransceiver;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.gpu.imageLoader.GeneralImageLoader;
import ds.mods.CCLights2.gpu.imageLoader.ImageLoader;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.item.ItemTablet;
import ds.mods.CCLights2.network.PacketHandler;

@Mod(modid = "CCLights2", name = "CCLights2", version = "0.2")
@NetworkMod(clientSideRequired = true, serverSideRequired = true, channels = {"CCLights2"}, packetHandler = PacketHandler.class)
public class CCLights2 {
	@Instance("CCLights2")
	public static CCLights2 instance;
	@SidedProxy(serverSide = "ds.mods.CCLights2.CommonProxy", clientSide = "ds.mods.CCLights2.client.ClientProxy")
	// start variables
	public static CommonProxy proxy;
	public static Block gpu;
	public static Block monitor;
	public static Block monitorBig;
	public static Block ttrans;
	public static Item ram;
	public static Item tablet;
	protected static Configuration config;
	
	public static CreativeTabs ccltab = new CreativeTabs("CClights2")
	 {
		 @Override
	  public ItemStack getIconItemStack()
	  {
	   return new ItemStack(Config.Tablet, 1, 0);
	  }
	 };

	// end variables

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Configuration config = new Configuration(
				event.getSuggestedConfigurationFile());
		Config.loadConfig(config);
		LanguageRegistry.instance().addStringLocalization(
				"itemGroup.CClights2", "en_US", "CCLights 2");
		// gpu
		NetworkRegistry.instance().registerGuiHandler(this, new GuiHandler());
		gpu = new BlockGPU(Config.Gpu, Material.iron);
		GameRegistry.registerBlock(gpu, "CCLGPU");
		LanguageRegistry.addName(gpu, "GPU");
		GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");
		GameRegistry.addRecipe(new ItemStack(gpu, 1), new Object[] { "III",
				"RGR", "GGG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',
				Item.ingotGold });
		monitor = new BlockMonitor(Config.Monitor, Material.iron);
		// monitor
		GameRegistry.registerBlock(monitor, "CCLMonitor");
		LanguageRegistry.addName(monitor, "Monitor");
		GameRegistry
				.registerTileEntity(TileEntityMonitor.class, "CCLMonitorTE");
		GameRegistry.addRecipe(new ItemStack(monitor, 2), new Object[] { "III",
				"RLR", "GGG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',
				Item.ingotGold, 'L', Block.thinGlass });
		// big monitor
		monitorBig = new BlockBigMonitor(Config.MonitorBig, Material.iron);
		GameRegistry.registerBlock(monitorBig, "CCLBigMonitor");
		LanguageRegistry.addName(monitorBig, "External Monitor");
		GameRegistry.registerTileEntity(TileEntityBigMonitor.class,
				"CCLBigMonitorTE");
		GameRegistry.addRecipe(new ItemStack(monitorBig, 8), new Object[] {
				"LLL", "LGL", "LLL", 'G', monitor, 'L', Block.thinGlass });
		
		// tablet trans
		ttrans = new BlockTabletTransceiver(Config.TTrans, Material.iron);
		GameRegistry.registerBlock(ttrans, "CCLTTrans");
		LanguageRegistry.addName(ttrans, "Tablet Transmitter");
		GameRegistry.registerTileEntity(TileEntityTTrans.class,
				"CCLTTransTE");
		GameRegistry.addRecipe(new ItemStack(ttrans, 1), new Object[] {
				" L ", "LGL", " L ", 'G', monitor, 'L', Item.redstone });
		
		// RAM
		ram = new ItemRAM(Config.Ram);
		GameRegistry.registerItem(ram, "CCLRAM");
		LanguageRegistry.addName(ram, "Random Access Memory");
		GameRegistry.addRecipe(new ItemStack(ram, 8), new Object[] { "III",
				"R R", "GGG", 'I', Item.ingotIron, 'R', Block.blockRedstone,
				'G', Item.ingotGold, 'L', Block.thinGlass });
		// Tablet
		tablet = new ItemTablet(Config.Tablet);
		GameRegistry.registerItem(tablet, "CCLTab");
		LanguageRegistry.addName(tablet, "Tablet");
		GameRegistry.addRecipe(new ItemStack(tablet, 2), new Object[] { "GIG",
				"RMR", "GIG", 'I', Item.ingotIron, 'R', Item.redstone, 'G',
				Item.ingotGold, 'M', monitorBig });
		
		ImageLoader.register(new GeneralImageLoader());
	}

	@EventHandler
	public void load(FMLInitializationEvent event) {
		proxy.registerRenderInfo();
		MinecraftForge.EVENT_BUS.register(new Events());
	}
}
