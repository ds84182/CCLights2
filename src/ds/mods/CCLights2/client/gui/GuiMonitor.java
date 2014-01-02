package ds.mods.CCLights2.client.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;


//DONE: Don't fire events when mouse is outside area, and apply correct offsets.
public class GuiMonitor extends GuiScreen {
	//private static final ResourceLocation corners = new ResourceLocation("cclights", "textures/gui/corners.png");
	public Monitor mon;
	public TileEntityMonitor tile;
	public boolean isMouseDown = false;
	public int mouseButton = 0;
	public int mlx;  // mouselast x
	public int mly;
	public int mx;   // mouse x
	public int my;
	
	public GuiMonitor(TileEntityMonitor mon)
	{
		this.mon = mon.mon;
		tile = mon;
	}
	
	public void initGui()
	{
		Texture tex = mon.tex;
		if (tex == null)
			throw new RuntimeException("OpenGL texture setup failed!");
		CCLights2.debug("Created textures");
		tex.img.getRGB(0, 0, tex.getWidth(), tex.getHeight(), TabletRenderer.dyntex_data, 0, 16*32);
		TabletRenderer.dyntex.updateDynamicTexture();
		Keyboard.enableRepeatEvents(true);
	}
	
	public int applyXOffset(int x)
	{
		return x-((width/4)-mon.getWidth()/4)*2;
	}
	
	public int applyYOffset(int y)
	{
		return y-((height/4)-mon.getHeight()/4)*2;
	}
	
	public int unapplyXOffset(int x)
	{
		return x+((width/4)-mon.getWidth()/4)*2;
	}
	
	public int unapplyYOffset(int y)
	{
		return y+((height/4)-mon.getHeight()/4)*2;
	}
	
	public void drawScreen(int par1, int par2, float par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		
		int wheel = Mouse.getDWheel();
		if (wheel != 0)
		{
			CCLights2.debug(wheel/120+"");
			PacketSenders.GPUEvent(par1,par2,tile,wheel);
		}
		if (isMouseDown)
		{
			if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
			{
				mx = par1;
				my = par2;
				if (mlx != mx | mly != my)
				{
					CCLights2.debug("Moused move!");
					PacketSenders.mouseEventMove(mx,mly,tile);
				}
				mlx = mx;
				mly = my;
			}
			else
			{
				mouseMovedOrUp(unapplyXOffset(par1)/2, unapplyYOffset(par2)/2, mouseButton);
			}
		}
		drawWorldBackground(0);
		Texture tex = mon.tex;
		synchronized (tex)
		{
			try {
				if (tex.renderLock) tex.wait(1L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			tex.img.getRGB(0, 0, tex.getWidth(), tex.getHeight(), TabletRenderer.dyntex_data, 0, 16*32);
		}
		TabletRenderer.dyntex.updateDynamicTexture();
		this.drawTexturedModalRect(unapplyXOffset(0), unapplyYOffset(0), mon.getWidth(), mon.getHeight());
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		
		
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
	
	public void drawTexturedModalRect(int x, int y, int w, int h)
    {
		GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        Tessellator var2 = Tessellator.instance;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        float var3 = 256.0F;
        GL11.glPushMatrix();
        GL11.glScaled(1D, 1D, 1D);
        var2.startDrawingQuads();
        //var2.setColorOpaque_I(4210752);
        var2.addVertexWithUV((double) x, (double) y, this.zLevel, 0.0D, 0D);
        var2.addVertexWithUV(x, (double)h+y, this.zLevel, 0.0D, h/(9*32D));
        var2.addVertexWithUV((double)w+x, (double)h+y, this.zLevel, w/(16*32D), h/(9*32D));
        var2.addVertexWithUV((double)w+x, y, this.zLevel, w/(16*32D), 0D);
        var2.draw();
        GL11.glPopMatrix();
    }
	
	protected void mouseClicked(int par1, int par2, int par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
		{
			CCLights2.debug("Mouse click! "+par3);
			isMouseDown = true;
			mouseButton = par3;
			mlx = par1;
			mx = par1;
			mly = par2;
			my = par2;
			PacketSenders.mouseEvent(mx,my,par3,tile);
		}
    }
	
	protected void mouseMovedOrUp(int par1, int par2, int par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (isMouseDown)
		{
			if (par3 == mouseButton)
			{
				CCLights2.debug("Mouse up! "+par3);
				isMouseDown = false;
                PacketSenders.mouseEventUp(tile);
			}
		}
    }

	protected void keyTyped(char par1, int par2)
    {
        super.keyTyped(par1, par2);
        if (par2 != 1)
        {
			  PacketSenders.sendKeyEvent(par1, par2,tile);
        }
    }
	
	public void onGuiClosed()
	{
		Keyboard.enableRepeatEvents(false);
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
