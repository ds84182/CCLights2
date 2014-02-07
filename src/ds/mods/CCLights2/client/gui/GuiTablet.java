package ds.mods.CCLights2.client.gui;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;
import ds.mods.CCLights2.utils.TabMesg;

public class GuiTablet extends GuiScreen {
	Monitor mon;
	Texture tex = TabletRenderer.defaultTexture;
    NBTTagCompound nbt;
	TileEntityMonitor tile;
	
	boolean isMouseDown = false;
	int mouseButton = 0;
	int mlx;
	int mly;
	int mx;
	int my;
	
	private int oldScale = Minecraft.getMinecraft().gameSettings.guiScale;
	
	public GuiTablet(NBTTagCompound n, World world)
	{
		nbt = n;
		if (nbt.getBoolean("canDisplay"))
		{
			UUID trans = UUID.fromString(nbt.getString("trans"));
			tile = (TileEntityMonitor) Minecraft.getMinecraft().theWorld.getBlockTileEntity((Integer)TabMesg.getTabVar(trans, "x"),(Integer)TabMesg.getTabVar(trans, "y"),(Integer)TabMesg.getTabVar(trans, "z"));
			mon = tile.mon;
			tex = mon.tex;
		}
	}
	
	public void initGui()
	{
		if (oldScale != 1)
		{
			oldScale = Minecraft.getMinecraft().gameSettings.guiScale;
			Minecraft.getMinecraft().gameSettings.guiScale = 1;
			ScaledResolution scaledresolution = new ScaledResolution(this.mc.gameSettings, this.mc.displayWidth, this.mc.displayHeight);
	        this.width  = scaledresolution.getScaledWidth();
	        this.height = scaledresolution.getScaledHeight();
		}
		Keyboard.enableRepeatEvents(true);
	}
	
	public int applyXOffset(int x)
	{
		return x-((width/4)-tex.getWidth()/4)*2;
	}
	
	public int applyYOffset(int y)
	{
		return y-((height/4)-tex.getHeight()/4)*2;
	}
	
	public int unapplyXOffset(int x)
	{
		return x+((width/4)-tex.getWidth()/4)*2;
	}
	
	public int unapplyYOffset(int y)
	{
		return y+((height/4)-tex.getHeight()/4)*2;
	}
	
	public void drawScreen(int x, int y, float par3)
    {
		x = applyXOffset(x);
		y = applyYOffset(y);
		if (nbt.getBoolean("canDisplay"))
		{
			int wheel = Mouse.getDWheel();
			if (wheel != 0)
			{
              PacketSenders.GPUEvent(x, y, tile, wheel);
			}
			if (isMouseDown)
			{
				if (x > -1 & y > -1 & x < mon.getWidth()+1 & y < mon.getHeight()+1)
				{
					mx = x;
					my = y;
					if (mlx != mx | mly != my)
					{
                        PacketSenders.mouseEventMove(mx, my, tile);
					}
					mlx = mx;
					mly = my;
				}
				else
				{
					mouseMovedOrUp(unapplyXOffset(x)/2, unapplyYOffset(y)/2, mouseButton);
				}
			}
		}
		drawWorldBackground(0);
		synchronized (tex)
		{
			try {
				if (tex.renderLock) {tex.wait(1L);}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		TextureUtil.uploadTexture(TabletRenderer.dyntex.getGlTextureId(), tex.rgbCache, 16*32, 9*32);
		this.drawTexturedModalRect(unapplyXOffset(0), unapplyYOffset(0), tex.getWidth(), tex.getHeight());
		GL11.glDisable(GL11.GL_TEXTURE_2D);
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
        var2.addVertexWithUV((double) x, (double) y, this.zLevel, 0.0D, 0D);
        var2.addVertexWithUV(x, (double)h+y, this.zLevel, 0.0D, h/(9*32D));
        var2.addVertexWithUV((double)w+x, (double)h+y, this.zLevel, w/(16*32D), h/(9*32D));
        var2.addVertexWithUV((double)w+x, y, this.zLevel, w/(16*32D), 0D);
        var2.draw();
        GL11.glPopMatrix();
    }
	
	protected void mouseClicked(int par1, int par2, int par3)
    {
		if (!nbt.getBoolean("canDisplay"))
			return;
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
		{
			isMouseDown = true;
			mouseButton = par3;
			mlx = par1;
			mx = par1;
			mly = par2;
			my = par2;
            PacketSenders.mouseEvent(par1, par2, par3, tile);
		}
    }
	
	protected void mouseMovedOrUp(int par1, int par2, int par3)
    {
		if (!nbt.getBoolean("canDisplay"))
			return;
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (isMouseDown)
		{
			if (par3 == mouseButton)
			{
				isMouseDown = false;
                PacketSenders.mouseEventUp(tile);
			}
		}
    }

	protected void keyTyped(char par1, int par2)
    {
        super.keyTyped(par1, par2);
        if (par2 > 1 && nbt.getBoolean("canDisplay"))
        {
			  PacketSenders.sendKeyEvent(par1, par2,tile);
        }
    }
	
	public void onGuiClosed()
	{
		Keyboard.enableRepeatEvents(false);
		Minecraft.getMinecraft().gameSettings.guiScale = oldScale;
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
