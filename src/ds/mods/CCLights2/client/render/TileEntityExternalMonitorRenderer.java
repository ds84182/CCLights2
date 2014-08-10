package ds.mods.CCLights2.client.render;

import java.awt.Color;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;
import ds.mods.CCLights2.gpu.Texture;

public class TileEntityExternalMonitorRenderer extends TileEntitySpecialRenderer implements ISimpleBlockRenderingHandler{
	
	public DynamicTexture texture;
	
	TextureManager re;
	
	TileEntityExternalMonitor tile = new TileEntityExternalMonitor();
	
	public TileEntityExternalMonitorRenderer()
	{
		re = Minecraft.getMinecraft().renderEngine;
		texture = new DynamicTexture(16*32, 9*32);
		tile.mon.tex.fill(Color.blue);
		tile.mon.tex.drawText("Hello,", 0, 0, Color.white);
		tile.mon.tex.drawText("World!", 0, 9, Color.white); //Yes, the fake ass wrap trick
		tile.mon.tex.texUpdate();
	}
	
	@Override
	public void renderTileEntityAt(TileEntity var1, double var2, double var4,
			double var6, float var8) {
		TileEntityExternalMonitor m = (TileEntityExternalMonitor) var1;
		if (!(m.m_xIndex == 0 && m.m_yIndex == 0)) return;
		GL11.glPushMatrix();
		GL11.glTranslated(var2, var4, var6);
		render(m);
		GL11.glPopMatrix();
	}
	
	public void render(TileEntityExternalMonitor m)
	{
		Tessellator t = Tessellator.instance;

		float z = m.m_width*Facing.offsetsZForSide[m.getRight()];
		float x = m.m_width*Facing.offsetsXForSide[m.getRight()];
		boolean x0 = x != 0;
		boolean z0 = z != 0;
		GL11.glTranslatef(x0 ? 1F : 0F, 0F, z0 ? 1F : 0F);
		GL11.glScalef(x0 ? Math.abs(x) : 1F, m.m_height, z0 ? Math.abs(z) : 1F);
		GL11.glTranslatef(x0 ? -0.5F : 0.5F, -0.5F, z0 ? -0.5F : 0.5F);
		
	    double rot = 0D;
	    float RenderFix = 0F;
		switch (m.m_dir) {
		case 0: rot = 180D;RenderFix = (-1F+(1/(float)m.m_width));break;
	    case 1: rot = 90D;  break;
	    case 2: break;
	    case 3: rot = 270D; RenderFix = (1F-(1/(float)m.m_width)); break; }
		GL11.glRotated(rot, 0, 1, 0);
		if (RenderFix != 0F && m.m_width > 1){ GL11.glTranslatef(RenderFix,0F,0F);}
		
		MakeBoxThatDoesNotHaveThisSide();
		Texture tex = m.mon.getTex();
		synchronized (tex)
		{
			try {
				if (tex.renderLock) tex.wait(1L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		TextureUtil.uploadTexture(texture.getGlTextureId(), tex.rgbCache, 16*32, 9*32);
		GL11.glDisable(GL11.GL_LIGHTING);
		t.startDrawingQuads();
		t.addVertexWithUV(-0.5F, 0.5F, 0.501F, 0F, (m.m_height*32F)/(9*32F));
		t.addVertexWithUV(0.5F, 0.5F, 0.501F, (m.m_width*32F)/(16*32F), (m.m_height*32F)/(9*32F));
		t.addVertexWithUV(0.5F, 1.5F, 0.501F, (m.m_width*32F)/(16*32F), 0F);
		t.addVertexWithUV(-0.5F, 1.5F, 0.501F, 0F, 0F);
		t.draw();
		GL11.glEnable(GL11.GL_LIGHTING);
	}

	private void MakeBoxThatDoesNotHaveThisSide() {
		ModelExternalMonitor model = new ModelExternalMonitor();
		model.renderModel();
	}

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelID,RenderBlocks renderer) {
		renderTileEntityAt(tile, 0D, -0D, 0D, 0F);
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
			Block block, int modelId, RenderBlocks renderer) {
		return false;
	}

	@Override
	public boolean shouldRender3DInInventory() {
		return true;
	}

	@Override
	public int getRenderId() {
		return CommonProxy.modelID;
	}

}
