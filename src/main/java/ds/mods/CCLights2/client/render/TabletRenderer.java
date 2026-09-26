package ds.mods.CCLights2.client.render;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ds.mods.CCLights2.block.tileentity.TileEntityTTrans;
import ds.mods.CCLights2.client.TabletLink;
import ds.mods.CCLights2.gpu.DrawState;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.item.ItemTablet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

/**
 * Renders the tablet item as a 3D slab with the paired transceiver's screen on it.
 */
@SideOnly(Side.CLIENT)
public class TabletRenderer implements IItemRenderer {
	private final ModelTablet model = new ModelTablet();
	private final ResourceLocation texture = new ResourceLocation("cclights", "textures/items/Tablet.png");

	public static final Texture defaultTexture = new Texture(TileEntityTTrans.WIDTH, TileEntityTTrans.HEIGHT);
	public static final Texture errorTexture = new Texture(TileEntityTTrans.WIDTH, TileEntityTTrans.HEIGHT);

	static {
		DrawState s = new DrawState();
		defaultTexture.setWantCache(true);
		defaultTexture.fill(new Color(20, 40, 90));
		s.color = Color.white;
		defaultTexture.drawText(s, "Not paired.", 8, 8);
		defaultTexture.drawText(s, "Right click a Tablet Transceiver with this tablet to pair it.", 8, 20);
		defaultTexture.drawText(s, "Then right click the tablet to open its screen.", 8, 30);
		defaultTexture.texUpdate();

		errorTexture.setWantCache(true);
		errorTexture.fill(new Color(120, 20, 20));
		errorTexture.drawText(s, "Out of range.", 8, 8);
		errorTexture.drawText(s, "Move closer to the transceiver this tablet is paired with.", 8, 20);
		errorTexture.texUpdate();
	}

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		return true;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
		return true;
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		Minecraft.getMinecraft().renderEngine.bindTexture(texture);
		GL11.glPushMatrix();
		switch (type) {
		case ENTITY:
			if (RenderItem.renderInFrame) {
				GL11.glRotatef(90, 0F, 0F, 1F);
				GL11.glTranslatef(0, -0.5F, 0.10F);
			} else {
				GL11.glRotatef(180, 0F, 0F, 1F);
				GL11.glTranslatef(0F, -0.25F, 0F);
			}
			break;
		case EQUIPPED: {
			GL11.glScalef(.5F, .5F, .5F);
			Entity entity = data.length > 1 && data[1] instanceof Entity ? (Entity) data[1] : null;
			if (entity instanceof EntityZombie) GL11.glTranslatef(0F, 0F, -0.5F);
			else GL11.glTranslatef(0F, 0.75F, -0.5F);
			GL11.glRotatef(90F + 60F, 1F, 0F, 0F);
			GL11.glRotatef(180F, 0F, 0F, 1F);
			break;
		}
		case EQUIPPED_FIRST_PERSON:
			GL11.glRotatef(-45F, 0F, 1F, 0F);
			GL11.glRotatef(-90F - 60F, 1F, 0F, 0F);
			GL11.glTranslatef(-0.75F, .25F, 2.75F);
			GL11.glScalef(4F, 1F, 4F);
			break;
		case INVENTORY:
			GL11.glRotatef(180, 0F, 0F, 1F);
			GL11.glRotatef(180, 0F, 1F, 0F);
			GL11.glTranslatef(0F, -0.25F, 0F);
			break;
		default:
			break;
		}
		model.draw();

		Texture tex = screenFor(item);
		GL11.glTranslatef(0F, -0.0001F, 0F);
		TextureCache.bind(tex);
		float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setNormal(0, -1, 0);
		double y = 0.5D - (2 / 16D);
		t.addVertexWithUV(-8 / 16D, y, -(6 / 16D), 0D, 1D);
		t.addVertexWithUV(0.5D, y, -(6 / 16D), 1D, 1D);
		t.addVertexWithUV(0.5D, y, (3 / 16D), 1D, 0D);
		t.addVertexWithUV(-8 / 16D, y, (3 / 16D), 0D, 0D);
		t.draw();
		GL11.glEnable(GL11.GL_LIGHTING);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
		GL11.glPopMatrix();
	}

	/** Picks what to show on a tablet: the transceiver screen, the pairing hint or the range error. */
	public static Texture screenFor(ItemStack item) {
		if (ItemTablet.getTransceiverId(item) == null) return defaultTexture;
		TileEntityTTrans tile = TabletLink.findTransceiver(item);
		if (tile == null || !TabletLink.inRange(tile)) return errorTexture;
		Texture tex = tile.getScreenTexture();
		return tex == null ? errorTexture : tex;
	}
}
