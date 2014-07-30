package ds.mods.CCLights2;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.Configuration;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import ds.mods.CCLights2.network.PacketHandler;

@Mod(modid = "CCLights2", name = "CCLights2", version = "0.4.1-75",dependencies="required-after:ComputerCraft@[1.6,)",acceptedMinecraftVersions = "1.6.4")
@NetworkMod(clientSideRequired = true, serverSideRequired = true, channels = { "CCLights2" },packetHandler = PacketHandler.class,connectionHandler = PacketHandler.class)
public class CCLights2 {
	@Mod.Instance("CCLights2")
	public static CCLights2 instance;
	
	@SidedProxy(serverSide = "ds.mods.CCLights2.CommonProxy", clientSide = "ds.mods.CCLights2.client.ClientProxy")
	public static CommonProxy proxy;
	
	public static Block gpu,monitor,monitorBig,light,advancedlight,ttrans;
	public static Item ram,tablet;
	public static Logger logger;
	
	public static CreativeTabs ccltab = new CreativeTabs("CClights2") {
		@Override
		public ItemStack getIconItemStack() {
			this.getTranslatedTabLabel();
			return new ItemStack(tablet.itemID, 1, 0);
		}
	};

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Config.loadConfig(new Configuration(event.getSuggestedConfigurationFile()));
		logger = event.getModLog();
		logger.setParent(FMLLog.getLogger());
		
		proxy.registerBlocks();
        
		logger.log(Level.INFO, "STANDING BY");
	}

	@Mod.EventHandler
	public void load(FMLPostInitializationEvent event) {
		proxy.registerRenderInfo();
        NetworkRegistry.instance().registerGuiHandler(this, new GuiHandler());
	}

	public static void debug(String debugmsg) {
		if (Config.DEBUGS) {
			logger.log(Level.INFO, debugmsg);
		}
	}
}
