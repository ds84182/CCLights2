package ds.mods.CCLights2.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;

import ds.mods.CCLights2.client.render.TabletItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Forge's custom item renderer for the tablet; the drawing is the common TabletItemRenderer. */
public final class ForgeTabletRenderer extends BlockEntityWithoutLevelRenderer {
	private static ForgeTabletRenderer instance;

	private ForgeTabletRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	public static synchronized ForgeTabletRenderer get() {
		if (instance == null) instance = new ForgeTabletRenderer();
		return instance;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
		TabletItemRenderer.render(stack, context, poseStack, buffers, light, overlay);
	}
}
