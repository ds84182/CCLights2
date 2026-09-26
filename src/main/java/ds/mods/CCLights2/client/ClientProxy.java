package ds.mods.CCLights2.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.render.ExternalMonitorRenderer;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.client.render.TextureCache;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.network.PacketSenders;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public class ClientProxy extends CommonProxy {
	private static ByteBuffer screenshotBuffer;
	private ClientDrawThread drawThread;

	@Override
	public World getClientWorld() {
		return Minecraft.getMinecraft().theWorld;
	}

	@Override
	public void registerHandlers() {
		super.registerHandlers();
		FMLCommonHandler.instance().bus().register(new ClientTickHandler());
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	public void registerRenderInfo() {
		CommonProxy.modelID = RenderingRegistry.getNextAvailableRenderId();
		ExternalMonitorRenderer renderer = new ExternalMonitorRenderer();
		RenderingRegistry.registerBlockHandler(renderer);
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityExternalMonitor.class, renderer);
		MinecraftForgeClient.registerItemRenderer(CCLights2.tablet, new TabletRenderer());
	}

	@Override
	public void runOnGameThread(Side side, Runnable task) {
		if (side == Side.CLIENT) {
			Minecraft.getMinecraft().func_152344_a(task);
		} else {
			super.runOnGameThread(side, task);
		}
	}

	@Override
	public void submitDraw(GPU gpu, List<DrawCMD> cmds) {
		if (drawThread == null || !drawThread.isAlive()) {
			drawThread = new ClientDrawThread();
			drawThread.start();
		}
		drawThread.submit(gpu, cmds);
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event) {
		if (!event.world.isRemote) return;
		if (drawThread != null) drawThread.clear();
		TextureCache.releaseAll();
		ClientTickHandler.pendingScreenshot = null;
	}

	@Override
	public File getWorldDir(World world) {
		return new File(FMLCommonHandler.instance().getMinecraftServerInstance().getFile("."), "saves/" + world.getSaveHandler().getWorldDirectoryName());
	}

	/** Reads the current framebuffer and sends it to the server for the tablet camera. */
	public static void takeScreenshot(TileEntityTTrans tile) {
		Minecraft mc = Minecraft.getMinecraft();
		int width = mc.displayWidth;
		int height = mc.displayHeight;
		int byteCount = width * height * 3;
		if (screenshotBuffer == null || screenshotBuffer.capacity() < byteCount) {
			screenshotBuffer = BufferUtils.createByteBuffer(byteCount);
		}
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		screenshotBuffer.clear();
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, screenshotBuffer);
		screenshotBuffer.rewind();

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int i = (x + width * y) * 3;
				int r = screenshotBuffer.get(i) & 0xFF;
				int g = screenshotBuffer.get(i + 1) & 0xFF;
				int b = screenshotBuffer.get(i + 2) & 0xFF;
				row[x] = (r << 16) | (g << 8) | b;
			}
			image.setRGB(0, height - y - 1, width, 1, row, 0, width);
		}
		PacketSenders.screenshot(tile, image);
	}
}
