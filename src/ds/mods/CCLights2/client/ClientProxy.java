package ds.mods.CCLights2.client;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.Display;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.render.SimpleBigMonitorRenderingHandler;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.client.render.TileEntityBigMonitorRenderer;
import ds.mods.CCLights2.network.PacketSenders;

public class ClientProxy extends CommonProxy {
	
	public static World getClientWorld()
	{
		return Minecraft.getMinecraft().theWorld;
	}
	
	public SimpleBigMonitorRenderingHandler SBMRH;
	
	@Override
	public void registerRenderInfo()
	{
		CommonProxy.modelID = RenderingRegistry.getNextAvailableRenderId();
		
		SBMRH = new SimpleBigMonitorRenderingHandler();
		RenderingRegistry.registerBlockHandler(SBMRH);
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityExternalMonitor.class, new TileEntityBigMonitorRenderer());
		MinecraftForgeClient.registerItemRenderer(CCLights2.tablet.itemID,new TabletRenderer());
        //ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAdvancedlight.class, new TileEntityLightRenderer());
	}
	
	@Override
	public File getWorldDir(World world)
	  {
	    return new File(FMLCommonHandler.instance().getMinecraftServerInstance().getFile("."), "saves/" + world.getSaveHandler().getWorldDirectoryName());
	  }
	
	public static void takeScreenshot(final TileEntityMonitor tile){
            	 try{
            		 //get mc window and capture it using robits
            		 FMLClientHandler.instance().getClient().gameSettings.hideGUI = true;
                     BufferedImage image = new Robot().createScreenCapture(new Rectangle(Display.getX() + 8, Display.getY() + 30, Display.getWidth(), Display.getHeight()));
                     FMLClientHandler.instance().getClient().gameSettings.hideGUI = false;
                     //send image to server
                     PacketSenders.screenshot(tile,image);
                 }catch(AWTException exception){
                     exception.printStackTrace();
                 }
	}
}
