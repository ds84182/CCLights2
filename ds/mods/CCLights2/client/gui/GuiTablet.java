package ds.mods.CCLights2.client.gui;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.network.PacketDispatcher;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.Texture;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;


//DONE: Don't fire events when mouse is outside area, and apply correct offsets.
public class GuiTablet extends GuiScreen {
	public GPU gpu;
	public NBTTagCompound nbt;
	public int texid;
	public ByteBuffer bbuf;
	public IntBuffer temp;
	public boolean isMouseDown = false;
	public int mouseButton = 0;
	public int mlx;
	public int mly;
	public int mx;
	public int my;
	public int lastWidth;//If these 2 values don't match, the texture will be deleted and regenerated.
	public int lastHeight;
	
	public GuiTablet(NBTTagCompound n, World world)
	{
		nbt = n;
		gpu = ((TileEntityGPU)world.getBlockTileEntity(n.getInteger("x"), n.getInteger("y"), n.getInteger("z"))).gpu;
	}
	
	public void initGui()
	{
		Texture tex = gpu.textures[0];
		if (tex == null)
			throw new RuntimeException("OpenGL texture setup failed!");
		nbt.setBoolean("gui", true);
		texid = GL11.glGenTextures();//GLAllocation.generateTextureNames();
		System.out.println("Created textures");
		bbuf = GLAllocation.createDirectByteBuffer(tex.bytedata.length);
		for (int i=0; i<bbuf.capacity(); i++)
		{
			bbuf.put(i, (byte) tex.bytedata[i]);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texid);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		System.out.println("Binded Texture");
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, tex.getWidth(), tex.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bbuf);
		System.out.println("Assigned texture contents");
		Keyboard.enableRepeatEvents(true);
		lastWidth = tex.getWidth();
		lastHeight = tex.getHeight();
	}
	
	public int applyXOffset(int x)
	{
		return x-((width/2)-gpu.textures[0].getWidth()/2);
	}
	
	public int applyYOffset(int y)
	{
		return y-((height/2)-gpu.textures[0].getHeight()/2);
	}
	
	public int unapplyXOffset(int x)
	{
		return x+((width/2)-gpu.textures[0].getWidth()/2);
	}
	
	public int unapplyYOffset(int y)
	{
		return y+((height/2)-gpu.textures[0].getHeight()/2);
	}
	
	public void drawScreen(int par1, int par2, float par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (isMouseDown)
		{
			if (par1 > -1 & par2 > -1 & par1 < gpu.textures[0].getWidth()+1 & par2 < gpu.textures[0].getHeight()+1)
			{
				mx = par1;
				my = par2;
				if (mlx != mx | mly != my)
				{
					if (Config.DEBUGS){
					System.out.println("Moused move!");}
					
//					Packet250CustomPayload packet = new Packet250CustomPayload();
//					packet.channel = "GPUMouse";
//					ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
//			    	DataOutputStream outputStream = new DataOutputStream(bos);
//			    	try {
//						outputStream.writeInt(gpu.tile.xCoord+gpu.tile.mondir.offsetX);
//						outputStream.writeInt(gpu.tile.yCoord+gpu.tile.mondir.offsetY);
//						outputStream.writeInt(gpu.tile.zCoord+gpu.tile.mondir.offsetZ);
//						outputStream.writeInt(gpu.tile.worldObj.provider.dimensionId);
//						outputStream.writeInt(1);
//						outputStream.writeInt(mx);
//						outputStream.writeInt(my);
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//			    	packet.data = bos.toByteArray();
//			    	packet.length = bos.size();
//			    	PacketDispatcher.sendPacketToServer(packet);
				}
				mlx = mx;
				mly = my;
			}
			else
			{
				mouseMovedOrUp(unapplyXOffset(Math.min(gpu.textures[0].getWidth(),par1)), unapplyYOffset(Math.min(gpu.textures[0].getHeight(),par2)), mouseButton);
			}
		}
		drawWorldBackground(0);
		if (lastWidth != gpu.textures[0].getWidth() || lastHeight != gpu.textures[0].getHeight())
		{
			onGuiClosed();
			initGui();
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texid);
        for (int i=0; i<bbuf.capacity(); i++)
		{
			bbuf.put(i, (byte) gpu.textures[0].bytedata[i]);
		}
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, gpu.textures[0].getWidth(), gpu.textures[0].getHeight(), GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bbuf);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		this.drawTexturedModalRect((width/2)-gpu.textures[0].getWidth()/2, (height/2)-gpu.textures[0].getHeight()/2, gpu.textures[0].getWidth(), gpu.textures[0].getHeight());
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
		if (par1 > -1 & par2 > -1 & par1 < gpu.textures[0].getWidth()+1 & par2 < gpu.textures[0].getHeight()+1)
		{
			if (Config.DEBUGS){
			System.out.println("Mouse click! "+par3);}
			isMouseDown = true;
			mouseButton = par3;
			mlx = par1;
			mx = par1;
			mly = par2;
			my = par2;
//			Packet250CustomPayload packet = new Packet250CustomPayload();
//			packet.channel = "GPUMouse";
//			ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
//	    	DataOutputStream outputStream = new DataOutputStream(bos);
//	    	try {
//	    		outputStream.writeInt(gpu.tile.xCoord+gpu.tile.mondir.offsetX);
//				outputStream.writeInt(gpu.tile.yCoord+gpu.tile.mondir.offsetY);
//				outputStream.writeInt(gpu.tile.zCoord+gpu.tile.mondir.offsetZ);
//				outputStream.writeInt(gpu.tile.worldObj.provider.dimensionId);
//				outputStream.writeInt(0);
//				outputStream.writeInt(par3);
//				outputStream.writeInt(par1);
//				outputStream.writeInt(par2);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//	    	packet.data = bos.toByteArray();
//	    	packet.length = bos.size();
//	    	PacketDispatcher.sendPacketToServer(packet);
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
//				Packet250CustomPayload packet = new Packet250CustomPayload();
//				packet.channel = "GPUMouse";
//				ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
//		    	DataOutputStream outputStream = new DataOutputStream(bos);
//		    	try {
//		    		outputStream.writeInt(gpu.tile.xCoord+gpu.tile.mondir.offsetX);
//					outputStream.writeInt(gpu.tile.yCoord+gpu.tile.mondir.offsetY);
//					outputStream.writeInt(gpu.tile.zCoord+gpu.tile.mondir.offsetZ);
//					outputStream.writeInt(gpu.tile.worldObj.provider.dimensionId);
//					outputStream.writeInt(2);
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//		    	packet.data = bos.toByteArray();
//		    	packet.length = bos.size();
//		    	PacketDispatcher.sendPacketToServer(packet);
			}
		}
    }
	
	public void sendKeyEvent(char par1 ,int par2)
	{
//		Packet250CustomPayload packet = new Packet250CustomPayload();
//		packet.channel = "GPUEvent";
//		ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
//    	DataOutputStream outputStream = new DataOutputStream(bos);
//    	try {
//    		outputStream.writeInt(gpu.tile.xCoord+gpu.tile.mondir.offsetX);
//			outputStream.writeInt(gpu.tile.yCoord+gpu.tile.mondir.offsetY);
//			outputStream.writeInt(gpu.tile.zCoord+gpu.tile.mondir.offsetZ);
//			outputStream.writeInt(gpu.tile.worldObj.provider.dimensionId);
//			outputStream.writeUTF("key");
//			outputStream.writeInt(1);
//			outputStream.writeInt(0);
//			outputStream.writeInt(par2);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//    	packet.data = bos.toByteArray();
//    	packet.length = bos.size();
//    	PacketDispatcher.sendPacketToServer(packet);
    	
    	if (ChatAllowedCharacters.isAllowedCharacter(par1))
    	{
//	    	packet = new Packet250CustomPayload();
//			packet.channel = "GPUEvent";
//			bos = new ByteArrayOutputStream(8);
//	    	outputStream = new DataOutputStream(bos);
//	    	try {
//	    		outputStream.writeInt(gpu.tile.xCoord+gpu.tile.mondir.offsetX);
//				outputStream.writeInt(gpu.tile.yCoord+gpu.tile.mondir.offsetY);
//				outputStream.writeInt(gpu.tile.zCoord+gpu.tile.mondir.offsetZ);
//				outputStream.writeInt(gpu.tile.worldObj.provider.dimensionId);
//				outputStream.writeUTF("char");
//				outputStream.writeInt(1);
//				outputStream.writeInt(2);
//				outputStream.writeChar(par1);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//	    	packet.data = bos.toByteArray();
//	    	packet.length = bos.size();
//	    	PacketDispatcher.sendPacketToServer(packet);
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
		nbt.setBoolean("gui", false);
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
