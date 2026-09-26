package ds.mods.CCLights2.client.render;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.BlockExternalMonitor;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.gpu.DrawState;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * Draws a whole multi-block monitor from its origin tile: the casing as textured block cells
 * and the screen as one full-bright quad whose texture is uploaded only when it changed.
 */
@SideOnly(Side.CLIENT)
public class ExternalMonitorRenderer extends TileEntitySpecialRenderer implements ISimpleBlockRenderingHandler {
	private static final float SCREEN_OFFSET = 0.002F;
	private static Monitor inventoryMonitor;

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
		if (!(te instanceof TileEntityExternalMonitor)) return;
		TileEntityExternalMonitor m = (TileEntityExternalMonitor) te;
		if (!m.isOrigin()) return;
		Monitor mon = m.getMonitor();
		GL11.glPushMatrix();
		GL11.glTranslated(x, y, z);
		render(m.getDir(), m.getWidth(), m.getHeight(), mon);
		GL11.glPopMatrix();
	}

	private static int rightOf(int dir) {
		switch (dir) {
		case 0: return 5;
		case 1: return 2;
		case 2: return 4;
		default: return 3;
		}
	}

	private static int frontOf(int dir) {
		switch (dir) {
		case 0: return 2;
		case 1: return 5;
		case 2: return 3;
		default: return 4;
		}
	}

	/** Renders a w x h monitor whose origin block occupies the unit cube at the current origin. */
	public static void render(int dir, int w, int h, Monitor mon) {
		int right = rightOf(dir);
		int front = frontOf(dir);
		int rx = Facing.offsetsXForSide[right], rz = Facing.offsetsZForSide[right];
		int fx = Facing.offsetsXForSide[front], fz = Facing.offsetsZForSide[front];

		Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationBlocksTexture);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		IIcon side = BlockExternalMonitor.iconSide, back = BlockExternalMonitor.iconBack, bezel = BlockExternalMonitor.iconFront;
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				float x0 = rx * i, z0 = rz * i, y0 = j;
				float x1 = x0 + 1, z1 = z0 + 1, y1 = y0 + 1;
				if (j == h - 1) faceYPos(t, x0, y1, z0, x1, z1, side);
				if (j == 0) faceYNeg(t, x0, y0, z0, x1, z1, side);
				// front (bezel, mostly hidden by the screen) and back
				emitSide(t, front, x0, y0, z0, x1, y1, z1, bezel);
				emitSide(t, front ^ 1, x0, y0, z0, x1, y1, z1, back);
				// the two ends of the row
				if (i == 0) emitSide(t, right ^ 1, x0, y0, z0, x1, y1, z1, side);
				if (i == w - 1) emitSide(t, right, x0, y0, z0, x1, y1, z1, side);
			}
		}
		t.draw();

		if (mon != null) {
			// Screen quad: on the front face plane, spanning all w x h blocks, pushed out slightly.
			float xa = rx > 0 ? 0 : (rx < 0 ? 1 : 0), za = rz > 0 ? 0 : (rz < 0 ? 1 : 0);
			// point A = start edge of the row (index 0 outer edge), point B = far edge of index w-1
			float ax = xa, az = za;
			float bx = xa + rx * w, bz = za + rz * w;
			// plane offset along the front normal
			float px = fx > 0 ? 1 + SCREEN_OFFSET : (fx < 0 ? -SCREEN_OFFSET : 0);
			float pz = fz > 0 ? 1 + SCREEN_OFFSET : (fz < 0 ? -SCREEN_OFFSET : 0);
			if (fx != 0) { ax = px; bx = px; }
			if (fz != 0) { az = pz; bz = pz; }
			if (rx != 0) { az = pz; bz = pz; ax = xa; bx = xa + rx * w; }
			if (rz != 0) { ax = px; bx = px; az = za; bz = za + rz * w; }
			boolean indexGrowsRight = dir == 1 || dir == 3;
			// U=0 must sit at the viewer's left edge.
			float leftX = indexGrowsRight ? ax : bx, leftZ = indexGrowsRight ? az : bz;
			float rightX = indexGrowsRight ? bx : ax, rightZ = indexGrowsRight ? bz : az;

			Texture tex = mon.tex;
			TextureCache.bind(tex);
			float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
			GL11.glDisable(GL11.GL_LIGHTING);
			t.startDrawingQuads();
			t.setColorOpaque_F(1F, 1F, 1F);
			t.setNormal(fx, 0, fz);
			t.addVertexWithUV(leftX, 0, leftZ, 0, 1);
			t.addVertexWithUV(rightX, 0, rightZ, 1, 1);
			t.addVertexWithUV(rightX, h, rightZ, 1, 0);
			t.addVertexWithUV(leftX, h, leftZ, 0, 0);
			t.draw();
			GL11.glEnable(GL11.GL_LIGHTING);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
		}
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	private static void emitSide(Tessellator t, int sideIndex, float x0, float y0, float z0, float x1, float y1, float z1, IIcon icon) {
		switch (sideIndex) {
		case 2: faceZNeg(t, x0, y0, z0, x1, y1, icon); break;
		case 3: faceZPos(t, x0, y0, z1, x1, y1, icon); break;
		case 4: faceXNeg(t, x0, y0, z0, y1, z1, icon); break;
		case 5: faceXPos(t, x1, y0, z0, y1, z1, icon); break;
		default: break;
		}
	}

	private static void faceYPos(Tessellator t, float x0, float y, float z0, float x1, float z1, IIcon i) {
		t.setNormal(0, 1, 0);
		t.addVertexWithUV(x1, y, z1, i.getMaxU(), i.getMaxV());
		t.addVertexWithUV(x1, y, z0, i.getMaxU(), i.getMinV());
		t.addVertexWithUV(x0, y, z0, i.getMinU(), i.getMinV());
		t.addVertexWithUV(x0, y, z1, i.getMinU(), i.getMaxV());
	}

	private static void faceYNeg(Tessellator t, float x0, float y, float z0, float x1, float z1, IIcon i) {
		t.setNormal(0, -1, 0);
		t.addVertexWithUV(x0, y, z1, i.getMinU(), i.getMaxV());
		t.addVertexWithUV(x0, y, z0, i.getMinU(), i.getMinV());
		t.addVertexWithUV(x1, y, z0, i.getMaxU(), i.getMinV());
		t.addVertexWithUV(x1, y, z1, i.getMaxU(), i.getMaxV());
	}

	private static void faceZNeg(Tessellator t, float x0, float y0, float z, float x1, float y1, IIcon i) {
		t.setNormal(0, 0, -1);
		t.addVertexWithUV(x0, y1, z, i.getMaxU(), i.getMinV());
		t.addVertexWithUV(x1, y1, z, i.getMinU(), i.getMinV());
		t.addVertexWithUV(x1, y0, z, i.getMinU(), i.getMaxV());
		t.addVertexWithUV(x0, y0, z, i.getMaxU(), i.getMaxV());
	}

	private static void faceZPos(Tessellator t, float x0, float y0, float z, float x1, float y1, IIcon i) {
		t.setNormal(0, 0, 1);
		t.addVertexWithUV(x0, y1, z, i.getMinU(), i.getMinV());
		t.addVertexWithUV(x0, y0, z, i.getMinU(), i.getMaxV());
		t.addVertexWithUV(x1, y0, z, i.getMaxU(), i.getMaxV());
		t.addVertexWithUV(x1, y1, z, i.getMaxU(), i.getMinV());
	}

	private static void faceXNeg(Tessellator t, float x, float y0, float z0, float y1, float z1, IIcon i) {
		t.setNormal(-1, 0, 0);
		t.addVertexWithUV(x, y1, z1, i.getMaxU(), i.getMinV());
		t.addVertexWithUV(x, y1, z0, i.getMinU(), i.getMinV());
		t.addVertexWithUV(x, y0, z0, i.getMinU(), i.getMaxV());
		t.addVertexWithUV(x, y0, z1, i.getMaxU(), i.getMaxV());
	}

	private static void faceXPos(Tessellator t, float x, float y0, float z0, float y1, float z1, IIcon i) {
		t.setNormal(1, 0, 0);
		t.addVertexWithUV(x, y0, z1, i.getMinU(), i.getMaxV());
		t.addVertexWithUV(x, y0, z0, i.getMaxU(), i.getMaxV());
		t.addVertexWithUV(x, y1, z0, i.getMaxU(), i.getMinV());
		t.addVertexWithUV(x, y1, z1, i.getMinU(), i.getMinV());
	}

	// ------------------------------------------------------------------ inventory

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
		if (inventoryMonitor == null) {
			inventoryMonitor = new Monitor(32, 32, null);
			DrawState s = new DrawState();
			inventoryMonitor.tex.fill(new Color(10, 20, 40));
			s.color = new Color(90, 180, 255);
			inventoryMonitor.tex.drawText(s, "CCL", 4, 8);
			s.color = Color.white;
			inventoryMonitor.tex.drawText(s, "2", 12, 18);
			inventoryMonitor.tex.texUpdate();
		}
		GL11.glPushMatrix();
		GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
		render(2, 1, 1, inventoryMonitor);
		GL11.glPopMatrix();
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
		return false;
	}

	@Override
	public boolean shouldRender3DInInventory(int modelId) {
		return true;
	}

	@Override
	public int getRenderId() {
		return CommonProxy.modelID;
	}
}
