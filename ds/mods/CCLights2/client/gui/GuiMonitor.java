package ds.mods.CCLights2.client.gui;

import java.awt.event.KeyListener;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.ChatAllowedCharacters;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.network.PacketDispatcher;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Monitor;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;


//DONE: Don't fire events when mouse is outside area, and apply correct offsets.
public class GuiMonitor extends GuiScreen {
	public Monitor mon;
	public TileEntityMonitor tile;
	public int texid;
	public ByteBuffer bbuf;
	public IntBuffer temp;
	public boolean isMouseDown = false;
	public int mouseButton = 0;
	public int mlx;
	public int mly;
	public int mx;
	public int my;
	
	public GuiMonitor(TileEntityMonitor mon)
	{
		this.mon = mon.mon;
		tile = mon;
	}
	
	public void initGui()
	{
		texid = GLAllocation.generateTextureNames();
		if (Config.DEBUGS){
		System.out.println("Created textures");}
		bbuf = GLAllocation.createDirectByteBuffer(mon.tex.bytedata.length);
		for (int i=0; i<bbuf.capacity(); i++)
		{
			bbuf.put(i, (byte) mon.tex.bytedata[i]);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texid);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        if (Config.DEBUGS){
        System.out.println("Binded Texture");}
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, mon.tex.getWidth(), mon.tex.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bbuf);
		if (Config.DEBUGS){
		System.out.println("Assigned texture contents");}
		Keyboard.enableRepeatEvents(true);
	}
	
	public int applyXOffset(int x)
	{
		return x-((width/2)-mon.tex.getWidth()/2);
	}
	
	public int applyYOffset(int y)
	{
		return y-((height/2)-mon.tex.getHeight()/2);
	}
	
	public int unapplyXOffset(int x)
	{
		return x+((width/2)-mon.tex.getWidth()/2);
	}
	
	public int unapplyYOffset(int y)
	{
		return y+((height/2)-mon.tex.getHeight()/2);
	}
	
	public void drawScreen(int par1, int par2, float par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (isMouseDown)
		{
			if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
			{
				mx = par1;
				my = par2;
				if (mlx != mx | mly != my)
				{
					if (Config.DEBUGS){
					System.out.println("Moused move!");}
					Packet250CustomPayload packet = new Packet250CustomPayload();
					packet.channel = "GPUMouse";
					ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
			    	DataOutputStream outputStream = new DataOutputStream(bos);
			    	try {
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
				mouseMovedOrUp(unapplyXOffset(Math.min(mon.tex.getWidth(),par1)), unapplyYOffset(Math.min(mon.tex.getHeight(),par2)), mouseButton);
			}
		}
		drawWorldBackground(0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texid);
        for (int i=0; i<bbuf.capacity(); i++)
		{
			bbuf.put(i, (byte) mon.tex.bytedata[i]);
		}
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, mon.tex.getWidth(), mon.tex.getHeight(), GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bbuf);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		this.drawTexturedModalRect((width/2)-mon.tex.getWidth()/2, (height/2)-mon.tex.getHeight()/2, mon.tex.getWidth(), mon.tex.getHeight());
		GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
	
	public void drawTexturedModalRect(int x, int y, int w, int h)
    {
		GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        Tessellator var2 = Tessellator.instance;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        float var3 = 256.0F;
        var2.startDrawingQuads();
        //var2.setColorOpaque_I(4210752);
        var2.addVertexWithUV(x, (double)h+y, this.zLevel, 0.0D, 1D);
        var2.addVertexWithUV((double)w+x, (double)h+y, this.zLevel, 1D, 1D);
        var2.addVertexWithUV((double)w+x, y, this.zLevel, 1D, 0D);
        var2.addVertexWithUV((double) x, (double) y, this.zLevel, 0.0D, 0D);
        var2.draw();
    }
	
	protected void mouseClicked(int par1, int par2, int par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
		{
			if (Config.DEBUGS){
			System.out.println("Mouse click! "+par3);}
			isMouseDown = true;
			mouseButton = par3;
			mlx = par1;
			mx = par1;
			mly = par2;
			my = par2;
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = "GPUMouse";
			ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
	    	DataOutputStream outputStream = new DataOutputStream(bos);
	    	try {
				outputStream.writeInt(tile.xCoord);
				outputStream.writeInt(tile.yCoord);
				outputStream.writeInt(tile.zCoord);
				outputStream.writeInt(tile.worldObj.provider.dimensionId);
				outputStream.writeInt(0);
				outputStream.writeInt(par3);
				outputStream.writeInt(par1);
				outputStream.writeInt(par2);
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
				if (Config.DEBUGS){
				System.out.println("Mouse up! "+par3);}
				isMouseDown = false;
				Packet250CustomPayload packet = new Packet250CustomPayload();
				packet.channel = "GPUMouse";
				ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
		    	DataOutputStream outputStream = new DataOutputStream(bos);
		    	try {
					outputStream.writeInt(tile.xCoord);
					outputStream.writeInt(tile.yCoord);
					outputStream.writeInt(tile.zCoord);
					outputStream.writeInt(tile.worldObj.provider.dimensionId);
					outputStream.writeInt(2);
				} catch (IOException e) {
					// TODO Auto-generated catch block
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
		packet.channel = "GPUEvent";
		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
    	DataOutputStream outputStream = new DataOutputStream(bos);
    	try {
			outputStream.writeInt(tile.xCoord);
			outputStream.writeInt(tile.yCoord);
			outputStream.writeInt(tile.zCoord);
			outputStream.writeInt(tile.worldObj.provider.dimensionId);
			outputStream.writeUTF("key");
			outputStream.writeInt(1);
			outputStream.writeInt(0);
			outputStream.writeInt(par2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	packet.data = bos.toByteArray();
    	packet.length = bos.size();
    	PacketDispatcher.sendPacketToServer(packet);
    	
    	if (ChatAllowedCharacters.isAllowedCharacter(par1))
    	{
	    	packet = new Packet250CustomPayload();
			packet.channel = "GPUEvent";
			bos = new ByteArrayOutputStream(8);
	    	outputStream = new DataOutputStream(bos);
	    	try {
				outputStream.writeInt(tile.xCoord);
				outputStream.writeInt(tile.yCoord);
				outputStream.writeInt(tile.zCoord);
				outputStream.writeInt(tile.worldObj.provider.dimensionId);
				outputStream.writeUTF("char");
				outputStream.writeInt(1);
				outputStream.writeInt(2);
				outputStream.writeChar(par1);
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		GL11.glDeleteTextures(texid);
		Keyboard.enableRepeatEvents(false);
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
