package ds.mods.CCLights2.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.client.TextureCache;
import ds.mods.CCLights2.gpu.Monitor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a monitor's screen on the front face of a single block (the plain monitor and the tablet
 * transceiver): letterboxed inside a thin bezel, full-bright, slightly in front of the block face.
 * <p>
 * Also holds the shared helpers: {@link #faceFront} puts the pose in a frame where the block is the
 * unit cube, its front face is the plane z = 1 facing +Z and +X is the viewer's right, and
 * {@link #drawScreen} emits one screen quad (texture U=0 at the viewer's left, V=0 at the top).
 */
public class ScreenFaceRenderer<T extends MonitorBlockEntity> implements BlockEntityRenderer<T> {
	/** Distance of the screen quad in front of the block face. */
	public static final float SCREEN_OFFSET = 0.005F;
	/** Bezel width on the single-block faces. */
	private static final float MARGIN = 1F / 16F;

	public ScreenFaceRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
		Monitor mon = be.getMonitor();
		if (mon == null) return;
		ResourceLocation loc = TextureCache.get(mon.tex);
		if (loc == null) return;
		int tw = Math.max(1, mon.getWidth()), th = Math.max(1, mon.getHeight());

		float avail = 1F - 2F * MARGIN;
		float qw, qh;
		if (tw >= th) {
			qw = avail;
			qh = avail * th / tw;
		} else {
			qh = avail;
			qw = avail * tw / th;
		}
		float x0 = 0.5F - qw / 2F, y0 = 0.5F - qh / 2F;

		poseStack.pushPose();
		faceFront(poseStack, be.getFacing());
		drawScreen(poseStack, buffers, loc, x0, y0, x0 + qw, y0 + qh, 1F + SCREEN_OFFSET);
		poseStack.popPose();
	}

	/**
	 * Rotates the pose (whose origin is the block's corner) so the block's {@code facing} side becomes
	 * the +Z face; local +X is then the right hand of someone looking at that face.
	 */
	public static void faceFront(PoseStack poseStack, Direction facing) {
		if (facing == null || facing.getAxis() == Direction.Axis.Y) facing = Direction.NORTH;
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
	}

	/** One full-bright quad in the plane z, facing +Z, textured with the whole screen texture. */
	public static void drawScreen(PoseStack poseStack, MultiBufferSource buffers, ResourceLocation texture,
			float x0, float y0, float x1, float y1, float z) {
		Matrix4f m = poseStack.last().pose();
		VertexConsumer vc = buffers.getBuffer(RenderType.text(texture));
		int light = LightTexture.FULL_BRIGHT;
		// Counter-clockwise seen from +Z (the viewer), so face culling keeps it.
		vc.vertex(m, x0, y0, z).color(255, 255, 255, 255).uv(0F, 1F).uv2(light).endVertex();
		vc.vertex(m, x1, y0, z).color(255, 255, 255, 255).uv(1F, 1F).uv2(light).endVertex();
		vc.vertex(m, x1, y1, z).color(255, 255, 255, 255).uv(1F, 0F).uv2(light).endVertex();
		vc.vertex(m, x0, y1, z).color(255, 255, 255, 255).uv(0F, 0F).uv2(light).endVertex();
	}
}
