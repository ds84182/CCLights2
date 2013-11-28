package ds.mods.CCLights2.client.render;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketHandler;

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
			float z = m.m_width*Facing.offsetsZForSide[m.getRight()];
			float x = m.m_width*Facing.offsetsXForSide[m.getRight()];
			GL11.glTranslatef((x != 0) ? 1F : 0F, 0F, (z != 0) ? 1F : 0F);
			GL11.glScalef((x != 0F) ? Math.abs(x) : 1F, m.m_height, (z != 0F) ? Math.abs(z) : 1F);
			GL11.glTranslatef((x != 0) ? -0.5F : 0.5F, -0.5F, (z != 0) ? -0.5F : 0.5F);
			switch (m.m_dir)
			{
			case 0:
				GL11.glRotated(-180, 0, 1, 0);
				if (m.m_width > 1) GL11.glTranslatef(-1F+(1/(float)m.m_width),0F,0F);
				break;
			case 1:
				GL11.glRotated(90, 0, 1, 0);
				break;
			case 2:
				break;
			case 3:
				GL11.glRotated(-90, 0, 1, 0);
				if (m.m_width > 1) GL11.glTranslatef(1F-(1/(float)m.m_width),0F,0F);
				break;
			default:
				System.out.println(m.m_dir);
			}
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor3f(1F, 1F, 1F);
			model.Shape1.render(0.0625F);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			Texture tex = m.mon.getTex();
			synchronized (tex)
			{
				try {
					if (tex.renderLock) tex.wait(1L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				tex.img.getRGB(0, 0, tex.getWidth(), tex.getHeight(), texture.getTextureData(), 0, 16*32);
			}
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
