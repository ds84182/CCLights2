package ds.mods.CCLights2;

import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketHandler;
import ds.mods.CCLights2.network.PacketHandler.PacketMessage;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.common.config.Configuration;

@Mod(modid = CCLights2.MODID, name = "CCLights2", version = CCLights2.VERSION, dependencies = "required-after:ComputerCraft@[1.7,)", acceptedMinecraftVersions = "[1.7.10]")
public class CCLights2 {
	public static final String MODID = "CCLights2";
	public static final String VERSION = "@VERSION@";

	@Mod.Instance(MODID)
	public static CCLights2 instance;

	@SidedProxy(serverSide = "ds.mods.CCLights2.CommonProxy", clientSide = "ds.mods.CCLights2.client.ClientProxy")
	public static CommonProxy proxy;

	public static Block gpu, monitor, monitorBig, ttrans;
	public static Item ram, tablet;
	public static Logger logger;
	public static SimpleNetworkWrapper network;

	public static CreativeTabs ccltab = new CreativeTabs("CCLights2") {
		@Override
		public Item getTabIconItem() {
			return tablet;
		}
	};

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		Config.loadConfig(new Configuration(event.getSuggestedConfigurationFile()));
		GPU.shaderMaxOps = Config.shaderMaxOpsPerPixel;
		GPU.shaderMaxPixels = Config.shaderMaxPixels;
		GPU.shaderMaxSource = Config.shaderMaxSourceBytes;
		loadFont();
		proxy.registerBlocks();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
		network.registerMessage(PacketHandler.class, PacketMessage.class, 0, Side.CLIENT);
		network.registerMessage(PacketHandler.class, PacketMessage.class, 1, Side.SERVER);
		NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
		proxy.registerHandlers();
		proxy.registerRenderInfo();
	}

	private static void loadFont() {
		InputStream in = CCLights2.class.getResourceAsStream("/assets/cclights/textures/gui/ascii.png");
		if (in == null) {
			logger.error("Font atlas assets/cclights/textures/gui/ascii.png is missing; drawText will do nothing");
			return;
		}
		try {
			Texture.loadFont(in);
		} catch (IOException e) {
			logger.error("Failed to load the CCLights2 font atlas", e);
		} finally {
			try {
				in.close();
			} catch (IOException ignored) {}
		}
	}

	public static void debug(String msg) {
		if (Config.DEBUG) logger.info(msg);
	}
}
