package ds.mods.CCLights2.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import ds.mods.CCLights2.client.TextureCache;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the tablet item as a thin slab whose front is the paired transceiver's live screen (or the
 * not-paired / no-signal picture). The item model is {@code builtin/entity}, so Minecraft applies the
 * model's display transforms and the corner-origin translation before calling this on every loader;
 * the loader modules only forward their custom-item-renderer hook here.
 */
public final class TabletItemRenderer {
	private TabletItemRenderer() {}

	private static final float PX = 1F / 16F;
	// slab: 16 px wide, 10 px tall, 1 px thick, standing upright in the XY plane with the screen on +Z
	private static final float X0 = 0F, X1 = 16 * PX;
	private static final float Y0 = 3 * PX, Y1 = 13 * PX;
	private static final float Z0 = 7.5F * PX, Z1 = 8.5F * PX;
	// screen inset by half a pixel of bezel: 15 x 9 px, close to the transceiver's 16:9
	private static final float SX0 = 0.5F * PX, SX1 = 15.5F * PX;
	private static final float SY0 = 3.5F * PX, SY1 = 12.5F * PX;

	public static void render(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
		Matrix4f m = poseStack.last().pose();
		ResourceLocation body = TextureCache.get(TabletTextures.body());
		if (body != null) {
			VertexConsumer vc = buffers.getBuffer(RenderType.text(body));
			quad(vc, m, light, X1, Y0, Z0, X0, Y0, Z0, X0, Y1, Z0, X1, Y1, Z0); // back
			quad(vc, m, light, X0, Y0, Z0, X1, Y0, Z0, X1, Y0, Z1, X0, Y0, Z1); // bottom
			quad(vc, m, light, X0, Y1, Z1, X1, Y1, Z1, X1, Y1, Z0, X0, Y1, Z0); // top
			quad(vc, m, light, X0, Y0, Z0, X0, Y0, Z1, X0, Y1, Z1, X0, Y1, Z0); // left
			quad(vc, m, light, X1, Y0, Z1, X1, Y0, Z0, X1, Y1, Z0, X1, Y1, Z1); // right
		}
		ResourceLocation bezel = TextureCache.get(TabletTextures.bezel());
		if (bezel != null) {
			VertexConsumer vc = buffers.getBuffer(RenderType.text(bezel));
			quad(vc, m, light, X0, Y0, Z1, X1, Y0, Z1, X1, Y1, Z1, X0, Y1, Z1); // front
		}
		ResourceLocation screen = TextureCache.get(TabletTextures.screenFor(stack));
		if (screen != null) ScreenFaceRenderer.drawScreen(poseStack, buffers, screen, SX0, SY0, SX1, SY1, Z1 + 0.002F);
	}

	/** One quad with the whole texture; vertices counter-clockwise seen from outside so culling keeps it. */
	private static void quad(VertexConsumer vc, Matrix4f m, int light,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.vertex(m, ax, ay, az).color(255, 255, 255, 255).uv(0F, 1F).uv2(light).endVertex();
		vc.vertex(m, bx, by, bz).color(255, 255, 255, 255).uv(1F, 1F).uv2(light).endVertex();
		vc.vertex(m, cx, cy, cz).color(255, 255, 255, 255).uv(1F, 0F).uv2(light).endVertex();
		vc.vertex(m, dx, dy, dz).color(255, 255, 255, 255).uv(0F, 0F).uv2(light).endVertex();
	}
}
