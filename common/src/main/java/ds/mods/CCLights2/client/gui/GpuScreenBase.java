package ds.mods.CCLights2.client.gui;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import ds.mods.CCLights2.block.entity.MonitorBlockEntity;
import ds.mods.CCLights2.client.TextureCache;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared behaviour of the monitor and tablet screens (port of GuiScreenBase): draws a texture
 * centred and scaled to fit inside a bevelled frame, maps mouse positions to screen pixels, and
 * forwards mouse and keyboard input to the server as ComputerCraft events.
 * <p>
 * Key codes are GLFW codes and are forwarded unchanged (CC: Tweaked's {@code keys} API uses them).
 * Mouse buttons are 0 left, 1 right, 2 middle, as in 1.7.10.
 */
public abstract class GpuScreenBase extends Screen {
	/** Frame width around the picture, in GUI units. */
	private static final int FRAME = 4;
	// Frame palette, taken from textures/gui/corners.png.
	private static final int OUTLINE = 0xFF232323;
	private static final int HIGHLIGHT = 0xFFFEFEFE;
	private static final int BODY = 0xFFC5C5C5;
	private static final int SHADOW = 0xFF5A5A5A;

	protected double scale = 1;
	protected int originX, originY;
	protected int screenW, screenH;

	private boolean mouseDown;
	private int mouseButton;
	private int lastPx = Integer.MIN_VALUE, lastPy = Integer.MIN_VALUE;
	private final IntSet heldKeys = new IntArraySet();

	protected GpuScreenBase(Component title) {
		super(title);
	}

	/** The texture to display, or null to show nothing. */
	@Nullable
	protected abstract Texture screenTexture();

	/** The monitor that receives input, or null when input should be ignored. */
	@Nullable
	protected abstract MonitorBlockEntity inputTarget();

	/** Extra drawing on top of the picture (status text); coordinates are GUI units. */
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		MonitorBlockEntity target = inputTarget();
		if (mouseDown) {
			mouseDown = false;
			if (target != null) PacketSenders.mouseUp(target);
		}
		if (target != null) {
			for (int key : heldKeys) PacketSenders.keyUp(key, target);
		}
		heldKeys.clear();
		super.removed();
	}

	private void layout(Texture tex) {
		screenW = Math.max(1, tex.getWidth());
		screenH = Math.max(1, tex.getHeight());
		double sx = (double) (width - 2 * FRAME - 4) / screenW;
		double sy = (double) (height - 2 * FRAME - 4) / screenH;
		scale = Math.max(0.05, Math.min(1.0, Math.min(sx, sy)));
		originX = (int) Math.round((width - screenW * scale) / 2);
		originY = (int) Math.round((height - screenH * scale) / 2);
	}

	/** Converts GUI coordinates to screen pixels; null when outside the screen. */
	@Nullable
	protected int[] toPixel(double mx, double my) {
		if (screenW <= 0 || screenH <= 0) return null;
		int px = (int) Math.floor((mx - originX) / scale);
		int py = (int) Math.floor((my - originY) / scale);
		if (px < 0 || py < 0 || px >= screenW || py >= screenH) return null;
		return new int[] { px, py };
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g);
		Texture tex = screenTexture();
		if (tex != null) {
			layout(tex);
			int w = (int) Math.round(screenW * scale);
			int h = (int) Math.round(screenH * scale);
			drawFrame(g, originX, originY, w, h);
			ResourceLocation loc = TextureCache.get(tex);
			if (loc != null) {
				g.blit(loc, originX, originY, w, h, 0F, 0F, screenW, screenH, screenW, screenH);
			}
		} else {
			screenW = screenH = 0;
		}
		renderOverlay(g, mouseX, mouseY, partialTick);
		super.render(g, mouseX, mouseY, partialTick);
	}

	private static void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
		int l = x - FRAME, t = y - FRAME, r = x + w + FRAME, b = y + h + FRAME;
		g.fill(l - 1, t - 1, r + 1, b + 1, OUTLINE);
		g.fill(l, t, r, b, BODY);
		g.fill(l, t, r - 1, t + 2, HIGHLIGHT);
		g.fill(l, t, l + 2, b - 1, HIGHLIGHT);
		g.fill(r - 2, t + 1, r, b, SHADOW);
		g.fill(l + 1, b - 2, r, b, SHADOW);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
	}

	// ------------------------------------------------------------------ mouse

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		MonitorBlockEntity target = inputTarget();
		int[] p = toPixel(mx, my);
		if (target == null || p == null) return super.mouseClicked(mx, my, button);
		if (mouseDown && mouseButton != button) PacketSenders.mouseUp(target);
		mouseDown = true;
		mouseButton = button;
		lastPx = p[0];
		lastPy = p[1];
		PacketSenders.mouseDown(p[0], p[1], button, target);
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (mouseDown && button == mouseButton) {
			mouseDown = false;
			MonitorBlockEntity target = inputTarget();
			if (target != null) PacketSenders.mouseUp(target);
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!mouseDown || button != mouseButton) return super.mouseDragged(mx, my, button, dx, dy);
		MonitorBlockEntity target = inputTarget();
		int[] p = toPixel(mx, my);
		if (target == null) {
			mouseDown = false;
		} else if (p == null) {
			// Dragged off the picture: end the drag like 1.7.10 did.
			mouseDown = false;
			PacketSenders.mouseUp(target);
		} else if (p[0] != lastPx || p[1] != lastPy) {
			lastPx = p[0];
			lastPy = p[1];
			PacketSenders.mouseMove(p[0], p[1], target);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		if (delta == 0) return false;
		MonitorBlockEntity target = inputTarget();
		int[] p = toPixel(mx, my);
		if (target == null || p == null) return false;
		PacketSenders.scroll(p[0], p[1], delta > 0 ? -1 : 1, target);
		return true;
	}

	// ------------------------------------------------------------------ keyboard

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scanCode, modifiers);
		MonitorBlockEntity target = inputTarget();
		if (target == null || key == GLFW.GLFW_KEY_UNKNOWN) return super.keyPressed(key, scanCode, modifiers);
		boolean repeat = !heldKeys.add(key);
		PacketSenders.keyDown(key, repeat, target);
		return true;
	}

	@Override
	public boolean keyReleased(int key, int scanCode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_UNKNOWN) return super.keyReleased(key, scanCode, modifiers);
		boolean wasHeld = heldKeys.remove(key);
		MonitorBlockEntity target = inputTarget();
		if (target == null || !wasHeld) return super.keyReleased(key, scanCode, modifiers);
		PacketSenders.keyUp(key, target);
		return true;
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		MonitorBlockEntity target = inputTarget();
		if (target == null || !SharedConstants.isAllowedChatCharacter(c)) return super.charTyped(c, modifiers);
		PacketSenders.charTyped(c, target);
		return true;
	}
}
