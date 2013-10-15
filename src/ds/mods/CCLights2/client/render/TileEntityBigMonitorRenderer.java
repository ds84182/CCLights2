package ds.mods.CCLights2.client.render;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.Texture;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;

public class TileEntityBigMonitorRenderer extends TileEntitySpecialRenderer {
	
	ModelLight model = new ModelLight();
	
	public DynamicTexture texture;
	
	TextureManager re;
	
	public IntBuffer temp;
	
	public TileEntityBigMonitorRenderer()
	{
		re = Minecraft.getMinecraft().renderEngine;
		texture = new DynamicTexture(16*32, 9*32);
	}
	
	@Override
	public void renderTileEntityAt(TileEntity var1, double var2, double var4,
			double var6, float var8) {
		GL11.glPushMatrix();
		GL11.glTranslated(var2, var4, var6);
		render((TileEntityBigMonitor) var1);
		GL11.glPopMatrix();
	}
	
	public void render(TileEntityBigMonitor m)
	{
		Tessellator t = Tessellator.instance;
		if (m.m_xIndex == 0 & m.m_yIndex == 0)
		{
			GL11.glTranslatef(0.5F-(m.m_width-1)/2F, 0F, +0.5F);
			GL11.glScalef(m.m_width, m.m_height, 1F);
			GL11.glTranslatef(0F, -0.5F, 0F);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor3f(1F, 1F, 1F);
			model.Shape1.render(0.0625F);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			Texture tex = m.mon.getTex();
			tex.img.getRGB(0, 0, tex.getWidth(), tex.getHeight(), texture.getTextureData(), 0, 16*32);
			texture.updateDynamicTexture();
			t.startDrawingQuads();
			t.addVertexWithUV(-0.5F, 0.5F, 0.501F, 0F, (m.m_height*32F)/(9*32F));
			t.addVertexWithUV(0.5F, 0.5F, 0.501F, (m.m_width*32F)/(16*32F), (m.m_height*32F)/(9*32F));
			t.addVertexWithUV(0.5F, 1.5F, 0.501F, (m.m_width*32F)/(16*32F), 0F);
			t.addVertexWithUV(-0.5F, 1.5F, 0.501F, 0F, 0F);
			t.draw();
		}
	}

}
