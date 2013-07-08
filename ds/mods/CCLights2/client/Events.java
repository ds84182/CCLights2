package ds.mods.CCLights2.client;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.ForgeSubscribe;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.client.render.ModelTablet;
import ds.mods.CCLights2.item.ItemTablet;

public class Events {
	ModelTablet model = new ModelTablet();
	@ForgeSubscribe
	public void renderOnScreen(RenderGameOverlayEvent event)
	{
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		ItemStack item = player.getHeldItem();
		if (item != null)
		{
			if (item.getItem() instanceof ItemTablet & Minecraft.getMinecraft().gameSettings.thirdPersonView == 0)
			{
				GuiIngameForge.renderCrosshairs = false;
				GL11.glPushMatrix();
					GL11.glTranslatef(event.resolution.getScaledWidth()/2F, event.resolution.getScaledHeight()*(.5F-(.5F/16F)), 0F);//Old position for when we get underlaying event.resolution.getScaledHeight()*(.75F+(.5F/16F))
					GL11.glScalef(event.resolution.getScaledHeight(),event.resolution.getScaledHeight(),1F);
					GL11.glRotatef(270F, 1F, 0F, 0F);
					Minecraft.getMinecraft().renderEngine.bindTexture("/mods/CCLights2/textures/items/Tablet.png");
					model.draw();
					NBTTagCompound nbt = ((ItemTablet)CCLights2.tablet).getNBT(item, Minecraft.getMinecraft().theWorld);
					if (nbt != null)
					{
						if (nbt.getBoolean("canDisplay") & !nbt.getBoolean("gui"))
						{
							TileEntityGPU tile = (TileEntityGPU) Minecraft.getMinecraft().theWorld.getBlockTileEntity(nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
							GPU gpu;
							if (tile != null)
							{
								gpu = tile.gpu;
								if (gpu.textures[0] != null)
								{
									//GL11.glTranslatef(0F, 0F, 0F);
									GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((ClientProxy)CCLights2.proxy).SBMRH.tileRender.textures[16][9]);
									ByteBuffer img = ((ClientProxy)CCLights2.proxy).SBMRH.tileRender.bbuf[16][9];
									for (int x = 0; x<gpu.textures[0].getWidth(); x++)
									{
										for (int y = 0; y<gpu.textures[0].getHeight(); y++)
										{
											img.put((((y*(16*32))+x)*3)+0, (byte) gpu.textures[0].bytedata[(((y*gpu.textures[0].getWidth())+x)*3)+0]);
											img.put((((y*(16*32))+x)*3)+1, (byte) gpu.textures[0].bytedata[(((y*gpu.textures[0].getWidth())+x)*3)+1]);
											img.put((((y*(16*32))+x)*3)+2, (byte) gpu.textures[0].bytedata[(((y*gpu.textures[0].getWidth())+x)*3)+2]);
										}
									}
									GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 16*32, 9*32, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, img);
									Tessellator tess = Tessellator.instance;
									tess.startDrawingQuads();
									//GL11.glDisable(GL11.GL_LIGHTING);0.5D-(2/16D),-8/16D,-(6/16D),0.5D,3/16D
									tess.addVertexWithUV(.5D,0D,-(6/16D),((double)gpu.textures[0].getWidth())/(16*32),0D);
									tess.addVertexWithUV(-8/16D, 0D, -(6/16D),0D,0D);
									tess.addVertexWithUV(-8/16D, 0D, (3/16D),0D,((double)gpu.textures[0].getHeight())/(9*32));
									tess.addVertexWithUV(.5D,0D, (3/16D),((double)gpu.textures[0].getWidth())/(16*32),((double)gpu.textures[0].getHeight())/(9*32));
									tess.draw();
									//GL11.glEnable(GL11.GL_LIGHTING);
								}
							}
						}
					}
					GL11.glPopMatrix();
				Minecraft.getMinecraft().renderEngine.bindTexture("/gui/icons.png");
			}
			else
			{
				GuiIngameForge.renderCrosshairs = true;
			}
		}
		else
		{
			GuiIngameForge.renderCrosshairs = true;
		}
	}
}
