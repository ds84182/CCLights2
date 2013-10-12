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

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.network.PacketDispatcher;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Convert;
import ds.mods.CCLights2.Monitor;
import ds.mods.CCLights2.Texture;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.network.PacketHandler;


//DONE: Don't fire events when mouse is outside area, and apply correct offsets.
public class GuiMonitor extends GuiScreen {
	public Monitor mon;
	public TileEntityMonitor tile;
	public int texid;
	public IntBuffer bbuf;
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
		Texture tex = mon.tex;
		if (tex == null)
			throw new RuntimeException("OpenGL texture setup failed!");
		System.out.println("Created textures");
		for (int x = 0; x<tex.getWidth(); x++)
		{
			for (int y = 0; y<tex.getHeight(); y++)
			{
				int[] rgb = Convert.toColorDepth(mon.tex.texture[(y*mon.getWidth())+x],mon.tex.bpp);
				TabletRenderer.dyntex_data[(y*(16*32))+x] = 0xFF<<24 | rgb[0]<<16 | rgb[1]<<8 | rgb[2];
			}
		}
		texid = ((ClientProxy)CCLights2.proxy).SBMRH.tileRender.textures[16][9];
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texid);
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
		for (int x = 0; x<mon.getWidth(); x++)
		{
			for (int y = 0; y<mon.getHeight(); y++)
			{
				int[] rgb = Convert.toColorDepth(mon.tex.texture[(y*mon.getWidth())+x],mon.tex.bpp);
				TabletRenderer.dyntex_data[(y*(16*32))+x] = 0xFF<<24 | rgb[0]<<16 | rgb[1]<<8 | rgb[2];
			}
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texid);
		TabletRenderer.dyntex.updateDynamicTexture();
		this.drawTexturedModalRect(unapplyXOffset(0)/2, unapplyYOffset(0)/2, mon.getWidth(), mon.getHeight());
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
        GL11.glScaled(2D, 2D, 1D);
        var2.startDrawingQuads();
        //var2.setColorOpaque_I(4210752);
        var2.addVertexWithUV((double) x, (double) y, this.zLevel, 0.0D, 0D);
        var2.addVertexWithUV(x, (double)h+y, this.zLevel, 0.0D, 1D);
        var2.addVertexWithUV((double)w+x, (double)h+y, this.zLevel, 1D, 1D);
        var2.addVertexWithUV((double)w+x, y, this.zLevel, 1D, 0D);
        var2.draw();
        GL11.glPopMatrix();
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
			// TODO Auto-generated catch block
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
		Keyboard.enableRepeatEvents(false);
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
