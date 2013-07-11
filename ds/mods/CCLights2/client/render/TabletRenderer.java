package ds.mods.CCLights2.client.render;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.item.ItemTablet;

public class TabletRenderer implements IItemRenderer {
	
	ModelTablet model = new ModelTablet();
	TextureManager re;

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		return true;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item,
			ItemRendererHelper helper) {
		return type != ItemRenderType.EQUIPPED & helper != ItemRendererHelper.BLOCK_3D;
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		if (re == null)
			re = Minecraft.getMinecraft().renderEngine;
		//re.bindTexture("/mods/CCLights2/textures/items/Tablet.png");
		GL11.glPushMatrix();
		GL11.glRotatef(90F, 1, 0, 0);
		if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.ENTITY)
		{
			GL11.glTranslatef(0F, 0F, -1F);
		}
		if (type == ItemRenderType.INVENTORY)
		{
			GL11.glRotatef(180F, 0, 0F, 1F);
			GL11.glTranslatef(0F, -0.5F, 0F);
		}
		if (type == ItemRenderType.ENTITY)
		{
			GL11.glTranslatef(0F, -0.5F, (9/16F));
			if (item.getTagCompound() == null)
			{
				GL11.glPopMatrix();
				return;
			}
		}
		if (type == ItemRenderType.EQUIPPED & Minecraft.getMinecraft().gameSettings.thirdPersonView == 0)
		{
			if (data[1].equals(Minecraft.getMinecraft().thePlayer))
			{
				GL11.glPopMatrix();
				return;
			}
		}
		else if (type == ItemRenderType.EQUIPPED)
		{
			GL11.glTranslatef(1F, -0.5F, 1F);
		}
		model.draw();
		NBTTagCompound nbt = ((ItemTablet)CCLights2.tablet).getNBT(item, Minecraft.getMinecraft().theWorld);
		if (nbt == null)
		{
			if (Config.DEBUGS){
			System.out.println("No NBT");}
			GL11.glPopMatrix();
			return;
		}
		if (!nbt.getBoolean("canDisplay"))
		{
			if (Config.DEBUGS){
			System.out.println("No Display");}
			GL11.glPopMatrix();
			return;
		}
		TileEntityGPU tile = (TileEntityGPU) Minecraft.getMinecraft().theWorld.getBlockTileEntity(nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
		GPU gpu;
		if (tile == null)
		{
			if (Config.DEBUGS){
			System.out.println("No GPU");}
			GL11.glPopMatrix();
			return;
		}
		else
		{
			gpu = tile.gpu;
		}
		if (gpu.textures[0] == null)
		{
			if (Config.DEBUGS){
			System.out.println("No Texture");}
			GL11.glPopMatrix();
			return;
		}
		GL11.glTranslatef(0F, -0.0001F, 0F);
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
		GL11.glDisable(GL11.GL_LIGHTING);
		tess.addVertexWithUV(-8/16D, 0.5D-(2/16D), -(6/16D),((double)gpu.textures[0].getWidth())/(16*32),0D);
		tess.addVertexWithUV(0.5D, 0.5D-(2/16D), -(6/16D),0D,0D);
		tess.addVertexWithUV(0.5D, 0.5D-(2/16D), (3/16D),0D,((double)gpu.textures[0].getHeight())/(9*32));
		tess.addVertexWithUV(-8/16D, 0.5D-(2/16D), (3/16D),((double)gpu.textures[0].getWidth())/(16*32),((double)gpu.textures[0].getHeight())/(9*32));
		tess.draw();
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glPopMatrix();
	}

}
