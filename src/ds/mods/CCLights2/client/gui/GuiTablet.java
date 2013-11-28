package ds.mods.CCLights2.client.gui;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.utils.Convert;
import ds.mods.CCLights2.utils.TabMesg;

public class GuiTablet extends GuiScreen {
	public Monitor mon;
	public Texture tex = TabletRenderer.defaultTexture;
	public NBTTagCompound nbt;
	public boolean isMouseDown = false;
	public int mouseButton = 0;
	public int mlx;
	public int mly;
	public int mx;
	public int my;
	
	public GuiTablet(NBTTagCompound n, World world)
	{
		nbt = n;
		if (nbt.getBoolean("canDisplay"))
		{
			UUID trans = UUID.fromString(nbt.getString("trans"));
			TileEntityMonitor tile = (TileEntityMonitor) Minecraft.getMinecraft().theWorld.getBlockTileEntity((Integer)TabMesg.getTabVar(trans, "x"),(Integer)TabMesg.getTabVar(trans, "y"),(Integer)TabMesg.getTabVar(trans, "z"));
			mon = tile.mon;
			tex = mon.tex;
		}
	}
	
	public void initGui()
	{
		nbt.setBoolean("gui", true);
		System.out.println("Created textures");
		tex.img.setRGB(0, 0, tex.getWidth(), tex.getHeight(), TabletRenderer.dyntex_data, 0, 16*32);
		TabletRenderer.dyntex.updateDynamicTexture();
		Keyboard.enableRepeatEvents(true);
	}
	
	public int applyXOffset(int x)
	{
		return x-((width/2)-tex.getWidth()/2);
	}
	
	public int applyYOffset(int y)
	{
		return y-((height/2)-tex.getHeight()/2);
	}
	
	public int unapplyXOffset(int x)
	{
		return x+((width/2)-tex.getWidth()/2);
	}
	
	public int unapplyYOffset(int y)
	{
		return y+((height/2)-tex.getHeight()/2);
	}
	
	public void drawScreen(int par1, int par2, float par3)
    {
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (nbt.getBoolean("canDisplay"))
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
					mouseMovedOrUp(unapplyXOffset(Math.min(mon.getWidth(),par1)), unapplyYOffset(Math.min(mon.getHeight(),par2)), mouseButton);
				}
			}
		drawWorldBackground(0);
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
		this.drawTexturedModalRect(applyXOffset(0)*4, applyYOffset(0)*4, tex.getWidth(), tex.getHeight());
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
        GL11.glScaled(0.5D, 0.5D, 1D);
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
		if (nbt.getBoolean("canDisplay"))
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
		if (nbt.getBoolean("canDisplay"))
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
		Keyboard.enableRepeatEvents(false);
		nbt.setBoolean("gui", false);
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
