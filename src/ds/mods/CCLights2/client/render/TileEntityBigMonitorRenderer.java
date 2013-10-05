package ds.mods.CCLights2.client.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;

public class TileEntityBigMonitorRenderer extends TileEntitySpecialRenderer {
	
	ModelLight model = new ModelLight();
	
	public int[][] textures = new int[17][10];
	
	TextureManager re;
	
	public ByteBuffer[][] bbuf = new ByteBuffer[17][10];//It only allocates this much to use less math when rendering
	
	public IntBuffer temp;
	
	public TileEntityBigMonitorRenderer()
	{
		for (int x = 1; x<17; x++)
		{
			for (int y = 1; y<10; y++)
			{
				textures[x][y] = GL11.glGenTextures();
				bbuf[x][y] = GLAllocation.createDirectByteBuffer(3*x*32*y*32);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[x][y]);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, x*32, y*32, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bbuf[x][y]);
		        if (Config.DEBUGS){
		        System.out.println("Made texture "+x+","+y);
		        }
			}
		}
	}
	
	@Override
	public void renderTileEntityAt(TileEntity var1, double var2, double var4,
			double var6, float var8) {
		if (re == null & this.tileEntityRenderer != null)
		{
			re = this.tileEntityRenderer.renderEngine;
		}
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
			/*(m.m_height/2F)
			if (m.m_height > 1)
			{
				GL11.glTranslated(0, (m.m_height-2D)-2/3D, 0);
			}*/
			//GL11.glTranslatef(-(m.m_width/(6F+m.m_width)), -(m.m_height/(6F+m.m_height)), 0F);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor3f(1F, 1F, 1F);
			model.Shape1.render(0.0625F);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			for (int i=0; i<bbuf[m.m_width][m.m_height].capacity(); i++)
			{
				if (i<m.mon.tex.bytedata.length)
					bbuf[m.m_width][m.m_height].put(i, (byte) m.mon.tex.bytedata[i]);
				else
					break;
			}
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[m.m_width][m.m_height]);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, m.m_width*32, m.m_height*32, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bbuf[m.m_width][m.m_height]);
			t.startDrawingQuads();
			t.addVertexWithUV(-0.5F, 0.5F, 0.501F, 0F, 1F);
			t.addVertexWithUV(0.5F, 0.5F, 0.501F, 1F, 1F);
			t.addVertexWithUV(0.5F, 1.5F, 0.501F, 1F, 0F);
			t.addVertexWithUV(-0.5F, 1.5F, 0.501F, 0F, 0F);
			t.draw();
		}
	}

}
