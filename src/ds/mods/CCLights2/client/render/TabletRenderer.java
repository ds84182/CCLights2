package ds.mods.CCLights2.client.render;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Convert;
import ds.mods.CCLights2.Monitor;
import ds.mods.CCLights2.Texture;
import ds.mods.CCLights2.block.tileentity.MonitorBase;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.ClientProxy;
import ds.mods.CCLights2.item.ItemTablet;
import ds.mods.CCLights2.utils.TabMesg;

public class TabletRenderer implements IItemRenderer {
	
	ModelTablet model = new ModelTablet();
	TextureManager re;
	ResourceLocation texture = new ResourceLocation("cclights", "textures/items/Tablet.png");
	public static DynamicTexture dyntex = new DynamicTexture(16*32,9*32);
	public static int[] dyntex_data;
	
	public TabletRenderer()
	{
		dyntex_data = dyntex.getTextureData();
	}

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		return true;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item,
			ItemRendererHelper helper) {
		return true;
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		if (re == null)
			re = Minecraft.getMinecraft().renderEngine;
		re.bindTexture(texture);
		//re.bindTexture("/mods/CCLights2/textures/items/Tablet.png");
		GL11.glPushMatrix();
		switch (type)
		{
		case ENTITY:
			GL11.glRotatef(180, 0F, 0F, 1F);
			GL11.glTranslatef(0F, -0.25F, 0F);
			break;
		case EQUIPPED:
			int i;
			for (i = 0; i<4; i++) {GL11.glPopMatrix();};
			for (i = 0; i<4; i++) {GL11.glPushMatrix();};
			GL11.glScalef(.5F, .5F, .5F);
			Entity entity = (Entity) data[1];
			if (entity instanceof EntityZombie)
				GL11.glTranslatef(0F, 0F, -0.5F);
			else
				GL11.glTranslatef(0F, 0.75F, -0.5F);
			GL11.glRotatef(90F+60F, 1F, 0F, 0F);
			GL11.glRotatef(180F, 0F, 0F, 1F);
			break;
		case EQUIPPED_FIRST_PERSON:
			GL11.glRotatef(-45F, 0F, 1F, 0F);
			GL11.glRotatef(-90F-60F, 1F, 0F, 0F);
			GL11.glTranslatef(-0.75F, .25F, 2.75F);
			GL11.glScalef(4F, 1F, 4F);
			break;
		case FIRST_PERSON_MAP:
			break;
		case INVENTORY:
			GL11.glRotatef(180, 0F, 0F, 1F);
			GL11.glRotatef(180, 0F, 1F, 0F);
			GL11.glTranslatef(0F, -0.25F, 0F);
			GL11.glScalef(1F, 1F, 1F);
			break;
		default:
			break;
		}
		{
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
			//Well, we need to get the screen :P
			UUID trans = UUID.fromString(nbt.getString("trans"));
			if (trans == null || TabMesg.getTabVar(trans, "x") == null) {nbt.setBoolean("canDisplay", false); GL11.glPopMatrix(); return;}
			if (Minecraft.getMinecraft().theWorld == null) {GL11.glPopMatrix(); return;}
			TileEntity noncast = Minecraft.getMinecraft().theWorld
					.getBlockTileEntity(
							(Integer)TabMesg.getTabVar(trans, "x"),
							(Integer)TabMesg.getTabVar(trans, "y"),
							(Integer)TabMesg.getTabVar(trans, "z"));
			if (noncast == null || !(noncast instanceof TileEntityTTrans)){nbt.setBoolean("canDisplay", false); GL11.glPopMatrix(); return;}
			MonitorBase tile = (MonitorBase) noncast;
			Monitor mon;
			mon = tile.mon;
			if (mon.tex == null)
			{
				if (Config.DEBUGS){
				System.out.println("No Texture");}
				GL11.glPopMatrix();
				return;
			}
			Texture tex = mon.tex;
			GL11.glTranslatef(0F, -0.0001F, 0F);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((ClientProxy)CCLights2.proxy).SBMRH.tileRender.textures[16][9]);
			//ByteBuffer img = ((ClientProxy)CCLights2.proxy).SBMRH.tileRender.bbuf[16][9];
			for (int x = 0; x<tex.getWidth(); x++)
			{
				for (int y = 0; y<tex.getHeight(); y++)
				{
					int[] rgb = Convert.toColorDepth(tex.texture[(y*tex.getWidth())+x],tex.bpp);
					dyntex_data[(y*(16*32))+x] = 0xFF<<24 | rgb[0]<<16 | rgb[1]<<8 | rgb[2];
				}
			}
			dyntex.updateDynamicTexture();
			//GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 16*32, 9*32, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, img);
			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			GL11.glDisable(GL11.GL_LIGHTING);
			tess.addVertexWithUV(-8/16D, 0.5D-(2/16D), -(6/16D),0D,((double)tex.getHeight())/(9*32));
			tess.addVertexWithUV(0.5D, 0.5D-(2/16D), -(6/16D),((double)tex.getWidth())/(16*32),((double)tex.getHeight())/(9*32));
			tess.addVertexWithUV(0.5D, 0.5D-(2/16D), (3/16D),((double)tex.getWidth())/(16*32),0D);
			tess.addVertexWithUV(-8/16D, 0.5D-(2/16D), (3/16D),0D,0D);
			tess.draw();
			GL11.glEnable(GL11.GL_LIGHTING);
		}
		GL11.glPopMatrix();
	}

}
