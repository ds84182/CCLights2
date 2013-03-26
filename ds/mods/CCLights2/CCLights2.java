package ds.mods.CCLights2;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.item.ItemRAM;


@Mod(modid="CCLights2",name="CCLights2",version="0.1")
@NetworkMod(clientSideRequired=true,serverSideRequired=false,channels={"GPUDrawlist","GPUEvent","GPUDownload","GPUMouse","GPUKey"}, packetHandler = PacketHandler.class)
public class CCLights2 {
	@Instance("CCLights2")
	public static CCLights2 instance;
	
	@SidedProxy(serverSide = "ds.mods.CCLights2.CommonProxy", clientSide = "ds.mods.CCLights2.client.ClientProxy")
	public static CommonProxy proxy;
	
	public static Block gpu;
	
	public static Block monitor;
	
	public static Item ram;
	
	@Init
    public void load(FMLInitializationEvent event)
    {
		NetworkRegistry.instance().registerGuiHandler(this, new GuiHandler());
		gpu = new BlockGPU(542, Material.iron);
		gpu.setTextureFile("/ds/mods/CCLights2/texture/terrain.png");
		gpu.blockIndexInTexture = 0;
		GameRegistry.registerBlock(gpu, "GPU");
		GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");
		monitor = new BlockMonitor(543, Material.iron);
        monitor.setTextureFile("/ds/mods/CCLights2/texture/terrain.png");
        monitor.blockIndexInTexture = 1;
		GameRegistry.registerBlock(monitor, "Monitor");
		GameRegistry.registerTileEntity(TileEntityMonitor.class, "Monitor");
		ram = new ItemRAM(544-256);
		GameRegistry.registerItem(ram, "RAM");
		LanguageRegistry.addName(ram, "RAM");
		proxy.registerRenderInfo();
    }
}
