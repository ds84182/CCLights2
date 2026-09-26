package ds.mods.CCLights2.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import ds.mods.CCLights2.block.entity.ExternalMonitorBlockEntity;
import ds.mods.CCLights2.client.TextureCache;
import ds.mods.CCLights2.gpu.Monitor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Draws a whole external monitor wall from its origin block: one full-bright quad covering all
 * width x height blocks, just in front of the front faces. The block models draw the casing.
 * <p>
 * Which way the wall extends from the origin (towards the viewer's right or left, up or down) is read
 * from the neighbouring blocks' indices, so the renderer does not depend on the merge algorithm's
 * orientation convention. The texture is always shown with U=0 at the viewer's left and V=0 at the top.
 */
public class ExternalMonitorRenderer implements BlockEntityRenderer<ExternalMonitorBlockEntity> {
	public ExternalMonitorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(ExternalMonitorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
		if (!be.isOrigin()) return;
		Monitor mon = be.getMonitor();
		if (mon == null) return;
		ResourceLocation loc = TextureCache.get(mon.tex);
		if (loc == null) return;

		int w = Math.max(1, be.getWidthBlocks());
		int h = Math.max(1, be.getHeightBlocks());
		Direction facing = be.getFacing();
		if (facing == null || facing.getAxis() == Direction.Axis.Y) facing = Direction.NORTH;
		Direction right = facing.getCounterClockWise();

		boolean growsRight = w <= 1 || isNext(be, right, facing, true) || !isNext(be, right.getOpposite(), facing, true);
		boolean growsUp = h <= 1 || isNext(be, Direction.UP, facing, false) || !isNext(be, Direction.DOWN, facing, false);
		float x0 = growsRight ? 0F : 1F - w;
		float y0 = growsUp ? 0F : 1F - h;

		poseStack.pushPose();
		ScreenFaceRenderer.faceFront(poseStack, facing);
		float z = 1F + ScreenFaceRenderer.SCREEN_OFFSET;
		if (w == 1 && h == 1 || wallIntact(be, right, w, h)) {
			ScreenFaceRenderer.drawScreen(poseStack, buffers, loc, x0, y0, x0 + w, y0 + h, z);
		} else {
			// The wall as this client knows it is not intact (the server has not re-laid it out yet, e.g.
			// part of it was changed while unloaded): draw only over the cells that still belong to it,
			// never over air or over blocks that now belong to another wall.
			for (int cy = 0; cy < h; cy++) {
				for (int cx = 0; cx < w; cx++) {
					if (!isMember(be, right, cx, cy, w, h)) continue;
					float u0 = (float) cx / w, u1 = (float) (cx + 1) / w;
					float v0 = 1F - (float) (cy + 1) / h, v1 = 1F - (float) cy / h;
					ScreenFaceRenderer.drawScreenPart(poseStack, buffers, loc, x0 + cx, y0 + cy, x0 + cx + 1, y0 + cy + 1, z, u0, v0, u1, v1);
				}
			}
		}
		poseStack.popPose();
	}

	private static boolean wallIntact(ExternalMonitorBlockEntity origin, Direction right, int w, int h) {
		for (int cy = 0; cy < h; cy++) for (int cx = 0; cx < w; cx++) if (!isMember(origin, right, cx, cy, w, h)) return false;
		return true;
	}

	/** True when the block at wall cell (cx, cy) exists and claims exactly that place in this wall. */
	private static boolean isMember(ExternalMonitorBlockEntity origin, Direction right, int cx, int cy, int w, int h) {
		if (cx == 0 && cy == 0) return true;
		Level level = origin.getLevel();
		if (level == null) return false;
		BlockEntity be = level.getBlockEntity(origin.getBlockPos().relative(right, cx).above(cy));
		return be instanceof ExternalMonitorBlockEntity m && m.getFacing() == origin.getFacing()
				&& m.getXIndex() == cx && m.getYIndex() == cy && m.getWidthBlocks() == w && m.getHeightBlocks() == h;
	}

	/** True when the block next to the origin in {@code dir} is the wall's index 1 along x (or y). */
	private static boolean isNext(ExternalMonitorBlockEntity origin, Direction dir, Direction facing, boolean alongX) {
		Level level = origin.getLevel();
		if (level == null) return false;
		BlockEntity be = level.getBlockEntity(origin.getBlockPos().relative(dir));
		if (!(be instanceof ExternalMonitorBlockEntity m) || m.isOrigin() || m.getFacing() != facing) return false;
		return alongX ? m.getXIndex() == 1 && m.getYIndex() == 0 : m.getXIndex() == 0 && m.getYIndex() == 1;
	}

	@Override
	public boolean shouldRenderOffScreen(ExternalMonitorBlockEntity be) {
		// A big wall stays visible when its origin block is outside the view frustum.
		return be.isOrigin();
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
