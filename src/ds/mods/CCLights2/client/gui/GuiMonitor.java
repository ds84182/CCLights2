package ds.mods.CCLights2.client.gui;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.ChatAllowedCharacters;
//import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.PacketDispatcher;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketHandler;
import ds.mods.CCLights2.utils.Convert;


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
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = "CCLights2";
	    	ByteArrayDataOutput out = ByteStreams.newDataOutput();
	    	
    		out.writeByte(PacketHandler.NET_GPUEVENT);
			out.writeInt(tile.xCoord);
			out.writeInt(tile.yCoord);
			out.writeInt(tile.zCoord);
			out.writeInt(tile.worldObj.provider.dimensionId);
			out.writeUTF("monitor_scroll");
			out.writeInt(3);
			
			out.writeInt(0);
			out.writeInt(par1);
			
			out.writeInt(0);
			out.writeInt(par2);
			
			out.writeInt(0);
			out.writeInt(wheel/120);
				
	    	packet.data = out.toByteArray();
	    	packet.length = packet.data.length;
	    	PacketDispatcher.sendPacketToServer(packet);
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
					Packet250CustomPayload packet = new Packet250CustomPayload();
					packet.channel = "CCLights2";
					ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
			    	DataOutputStream outputStream = new DataOutputStream(bos);
			    	try {
			    		outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
						outputStream.writeInt(tile.xCoord);
						outputStream.writeInt(tile.yCoord);
						outputStream.writeInt(tile.zCoord);
						outputStream.writeInt(tile.worldObj.provider.dimensionId);
						outputStream.writeInt(1);
						outputStream.writeInt(mx);
						outputStream.writeInt(my);
					} catch (IOException e) {
						e.printStackTrace();
					}
			    	packet.data = bos.toByteArray();
			    	packet.length = bos.size();
			    	PacketDispatcher.sendPacketToServer(packet);
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
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = "CCLights2";
			ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
	    	DataOutputStream outputStream = new DataOutputStream(bos);
	    	try {
	    		outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
				outputStream.writeInt(tile.xCoord);
				outputStream.writeInt(tile.yCoord);
				outputStream.writeInt(tile.zCoord);
				outputStream.writeInt(tile.worldObj.provider.dimensionId);
				outputStream.writeInt(0);
				outputStream.writeInt(par3);
				outputStream.writeInt(par1);
				outputStream.writeInt(par2);
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	packet.data = bos.toByteArray();
	    	packet.length = bos.size();
	    	PacketDispatcher.sendPacketToServer(packet);
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
				Packet250CustomPayload packet = new Packet250CustomPayload();
				packet.channel = "CCLights2";
				ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		    	DataOutputStream outputStream = new DataOutputStream(bos);
		    	try {
		    		outputStream.writeByte(PacketHandler.NET_GPUMOUSE);
					outputStream.writeInt(tile.xCoord);
					outputStream.writeInt(tile.yCoord);
					outputStream.writeInt(tile.zCoord);
					outputStream.writeInt(tile.worldObj.provider.dimensionId);
					outputStream.writeInt(2);
				} catch (IOException e) {
					e.printStackTrace();
				}
		    	packet.data = bos.toByteArray();
		    	packet.length = bos.size();
		    	PacketDispatcher.sendPacketToServer(packet);
			}
		}
    }
	
	public void sendKeyEvent(char par1 ,int par2)
	{
		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "CCLights2";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
    	DataOutputStream outputStream = new DataOutputStream(bos);
    	try {
    		outputStream.writeByte(PacketHandler.NET_GPUEVENT);
			outputStream.writeInt(tile.xCoord);
			outputStream.writeInt(tile.yCoord);
			outputStream.writeInt(tile.zCoord);
			outputStream.writeInt(tile.worldObj.provider.dimensionId);
			outputStream.writeUTF("key");
			outputStream.writeInt(1);
			outputStream.writeInt(0);
			outputStream.writeInt(par2);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	packet.data = bos.toByteArray();
    	packet.length = bos.size();
    	PacketDispatcher.sendPacketToServer(packet);
    	
    	if (ChatAllowedCharacters.isAllowedCharacter(par1))
    	{
	    	packet = new Packet250CustomPayload();
			packet.channel = "CCLights2";
			bos = new ByteArrayOutputStream(8);
	    	outputStream = new DataOutputStream(bos);
	    	try {
	    		outputStream.writeByte(PacketHandler.NET_GPUEVENT);
				outputStream.writeInt(tile.xCoord);
				outputStream.writeInt(tile.yCoord);
				outputStream.writeInt(tile.zCoord);
				outputStream.writeInt(tile.worldObj.provider.dimensionId);
				outputStream.writeUTF("char");
				outputStream.writeInt(1);
				outputStream.writeInt(2);
				outputStream.writeChar(par1);
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	packet.data = bos.toByteArray();
	    	packet.length = bos.size();
	    	PacketDispatcher.sendPacketToServer(packet);
    	}
	}

	protected void keyTyped(char par1, int par2)
    {
        super.keyTyped(par1, par2);
        if (par2 != 1)
        {
			  sendKeyEvent(par1, par2);
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
