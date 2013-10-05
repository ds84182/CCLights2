package ds.mods.CCLights2.client;

import java.io.IOException;

import javax.xml.stream.util.EventReaderDelegate;

import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.client.TextureFXManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.client.render.SimpleBigMonitorRenderingHandler;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.client.render.TileEntityBigMonitorRenderer;

public class ClientProxy extends CommonProxy {
	public World getClientWorld()
	{
		return Minecraft.getMinecraft().theWorld;
	}
	
	public SimpleBigMonitorRenderingHandler SBMRH;
	
	public void registerRenderInfo()
	{
		this.modelID = RenderingRegistry.getNextAvailableRenderId();
		SBMRH = new SimpleBigMonitorRenderingHandler();
		RenderingRegistry.registerBlockHandler(SBMRH);
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityBigMonitor.class, new TileEntityBigMonitorRenderer());
		MinecraftForgeClient.registerItemRenderer(CCLights2.tablet.itemID,new TabletRenderer());
		MinecraftForge.EVENT_BUS.register(new Events());
	}
	 public static CreativeTabs ccltab = new CreativeTabs("CClights2")
	 {
		 @Override
	  public ItemStack getIconItemStack()
	  {
	   return new ItemStack(Config.Tablet, 1, 0);
	  }
	 };
}
