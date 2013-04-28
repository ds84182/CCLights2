package ds.mods.CCLights2;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import ds.mods.CCLights2.block.BlockBigMonitor;
import ds.mods.CCLights2.block.BlockGPU;
import ds.mods.CCLights2.block.BlockMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.item.ItemTablet;


@Mod(modid="CCLights2",name="CCLights2",version="0.1")
@NetworkMod(clientSideRequired=true,serverSideRequired=true,channels={"GPUDrawlist","GPUEvent","GPUDownload","GPUMouse","GPUKey","GPUTile"}, packetHandler = PacketHandler.class)
public class CCLights2 {
	@Instance("CCLights2")
	public static CCLights2 instance;
	
	@SidedProxy(serverSide = "ds.mods.CCLights2.CommonProxy", clientSide = "ds.mods.CCLights2.client.ClientProxy")
	public static CommonProxy proxy;
	
	public static Block gpu;
	
	public static Block monitor;
	
	public static Block monitorBig;
	
	public static Item ram;
	
	public static Item tablet;
	
	@Init
    public void load(FMLInitializationEvent event)
    {
		NetworkRegistry.instance().registerGuiHandler(this, new GuiHandler());
		gpu = new BlockGPU(542, Material.iron);
		//gpu.setTextureFile("/ds/mods/CCLights2/texture/terrain.png");
		//gpu.blockIndexInTexture = 0;
		GameRegistry.registerBlock(gpu, "CCLGPU");
		GameRegistry.registerTileEntity(TileEntityGPU.class, "GPU");
		GameRegistry.addRecipe(new ItemStack(gpu,1),new Object[]{
			"III",
			"RGR",
			"GGG",'I',Item.ingotIron,'R',Item.redstone,'G',Item.ingotGold});
		monitor = new BlockMonitor(543, Material.iron);
        //monitor.setTextureFile("/ds/mods/CCLights2/texture/terrain.png");
        //monitor.blockIndexInTexture = 1;
		GameRegistry.registerBlock(monitor, "CCLMonitor");
		GameRegistry.registerTileEntity(TileEntityMonitor.class, "CCLMonitorTE");
		GameRegistry.addRecipe(new ItemStack(monitor,2),new Object[]{
			"III",
			"RLR",
			"GGG",'I',Item.ingotIron,'R',Item.redstone,'G',Item.ingotGold,'L',Block.thinGlass});
		monitorBig = new BlockBigMonitor(545, Material.iron);
		GameRegistry.registerBlock(monitorBig, "CCLBigMonitor");
		GameRegistry.registerTileEntity(TileEntityBigMonitor.class, "CCLBigMonitorTE");
		GameRegistry.addRecipe(new ItemStack(monitorBig,16),new Object[]{
			"LLL",
			"LGL",
			"LLL",'G',monitor,'L',Block.thinGlass});
		ram = new ItemRAM(4097-256);
		GameRegistry.registerItem(ram, "CCLRAM");
		LanguageRegistry.addName(ram, "RAM");
		GameRegistry.addRecipe(new ItemStack(ram,8),new Object[]{
			"III",
			"R R",
			"GGG",'I',Item.ingotIron,'R',Block.blockRedstone,'G',Item.ingotGold,'L',Block.thinGlass});
		tablet = new ItemTablet(4098);
		GameRegistry.registerItem(tablet, "CCLTab");
		LanguageRegistry.addName(tablet, "Tablet");
		GameRegistry.addRecipe(new ItemStack(tablet,2),new Object[]{
			"GIG",
			"RMR",
			"GIG",'I',Item.ingotIron,'R',Item.redstone,'G',Item.ingotGold,'M',monitorBig});
		proxy.registerRenderInfo();
		MinecraftForge.EVENT_BUS.register(new Events());
    }
}
