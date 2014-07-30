package ds.mods.CCLights2.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.render.SimpleBigMonitorRenderingHandler;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.client.render.TileEntityBigMonitorRenderer;
import ds.mods.CCLights2.network.PacketSenders;

public class ClientProxy extends CommonProxy {
	private static ByteBuffer ssBuffer;

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

		TickRegistry.registerTickHandler(new ClientTickHandler(), Side.CLIENT);
	}

	@Override
	public File getWorldDir(World world)
	{
		return new File(FMLCommonHandler.instance().getMinecraftServerInstance().getFile("."), "saves/" + world.getSaveHandler().getWorldDirectoryName());
	}

	public static void takeScreenshot(TileEntityTTrans tile){
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

		int width = Minecraft.getMinecraft().displayWidth;
		int height = Minecraft.getMinecraft().displayHeight;
		int byteCount = width * height * 3;

		if (ssBuffer == null || ssBuffer.capacity() < byteCount) {
			ssBuffer = BufferUtils.createByteBuffer(byteCount);
		}

		ssBuffer.clear();
		// read the BGR values into the image
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, ssBuffer);

		ssBuffer.rewind();

		byte[] data = new byte[byteCount];
		ssBuffer.get(data);
		BufferedImage bufferedimage = new BufferedImage(width, height, 1);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int i = (x + (width * y)) * 3;
				int r = data[i] & 0xFF;
				int g = data[i + 1] & 0xFF;
				int b = data[i + 2] & 0xFF;
				bufferedimage.setRGB(x, height - y - 1, (0xFF << 24) | (r << 16) | (g << 8) | b);
			}
		}

		//send image to server
		PacketSenders.screenshot(tile,bufferedimage);
	}

	private static void func_74289_a(int[] par0ArrayOfInteger, int par1, int par2)
	{
		int[] aint1 = new int[par1];
		int k = par2 / 2;

		for (int l = 0; l < k; ++l)
		{
			System.arraycopy(par0ArrayOfInteger, l * par1, aint1, 0, par1);
			System.arraycopy(par0ArrayOfInteger, (par2 - 1 - l) * par1, par0ArrayOfInteger, l * par1, par1);
			System.arraycopy(aint1, 0, par0ArrayOfInteger, (par2 - 1 - l) * par1, par1);
		}
	}
}
