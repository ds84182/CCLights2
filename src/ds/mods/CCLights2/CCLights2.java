package ds.mods.CCLights2;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import ds.mods.CCLights2.gpu.imageLoader.GeneralImageLoader;
import ds.mods.CCLights2.gpu.imageLoader.ImageLoader;
import ds.mods.CCLights2.network.PacketHandler;
import ds.mods.CCLights2.utils.LangHelper;

@Mod(modid = "CCLights2", name = "CCLights2", version = "0.4",dependencies="required-after:ComputerCraft;required-after:CCTurtle")
@NetworkMod(clientSideRequired = true, serverSideRequired = true, channels = { "CCLights2" }, packetHandler = PacketHandler.class)
public class CCLights2 {
	@Mod.Instance("CCLights2")
	public static CCLights2 instance;
	
	@SidedProxy(serverSide = "ds.mods.CCLights2.CommonProxy", clientSide = "ds.mods.CCLights2.client.ClientProxy")
	// start variables
	public static CommonProxy proxy;
	
	public static Block gpu,monitor,monitorBig,light,advancedlight,ttrans;
	public static Item ram,tablet;
	
	//protected static Configuration config;
	public static Logger logger;
	
	public static boolean gzip;

	public static CreativeTabs ccltab = new CreativeTabs("CClights2") {
		@Override
		public ItemStack getIconItemStack() {
			this.getTranslatedTabLabel();
			return new ItemStack(Config.Tablet, 1, 0);
		}
	};

	// end variables

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		//Configuration config = ;
		Config.loadConfig(new Configuration(event.getSuggestedConfigurationFile()));
		ImageLoader.register(new GeneralImageLoader());
		logger = event.getModLog();
		logger.setParent(FMLLog.getLogger());
		
		proxy.init();
        
		logger.log(Level.INFO, "STANDING BY");
	}

	@Mod.EventHandler
	public void load(FMLInitializationEvent event) {
		proxy.registerRenderInfo();
        NetworkRegistry.instance().registerGuiHandler(CCLights2.class, new GuiHandler());
		MinecraftForge.EVENT_BUS.register(new Events());
	}

	public static void debug(String debugmsg) {
		if (Config.DEBUGS) {
			Level level = Level.INFO;
			logger.log(level, debugmsg);
		}
	}
}
