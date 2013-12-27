package ds.mods.CCLights2.client.render;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.block.tileentity.TileEntityaAdvancedlight;

public class TileEntityLightRenderer extends TileEntitySpecialRenderer {
	private ModelLight model;
	public TileEntityLightRenderer() {
		model = new ModelLight();
	}

	public void renderAModelAt(TileEntityaAdvancedlight tile, double d,double d1, double d2, float f) {
		bindTexture(new ResourceLocation("cclights", "textures/blocks/Light.png"));
		GL11.glPushMatrix(); // start
		GL11.glColor3f((float) tile.r / 255, (float) tile.g / 255,(float) tile.b / 255);
		GL11.glTranslatef((float) d + 0.5F, (float) d1 + 1.5F,(float) d2 + 0.5F);
		GL11.glScalef(1.0F, -1F, -1F);
		GL11.glDisable(GL11.GL_LIGHTING);
		model.Shape1.render(0.0625F); // renders and yes 0.0625 is a random number
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glColor3f(1, 1, 1);
		GL11.glPopMatrix(); // end

	}

	public void renderTileEntityAt(TileEntity tileentity, double d, double d1,double d2, float f) {
		renderAModelAt((TileEntityaAdvancedlight) tileentity, d, d1, d2, f); // where to render
	}
}
