package ds.mods.CCLights2.client.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.render.TextureCache;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatAllowedCharacters;

/**
 * Shared behaviour of the monitor and tablet screens: draws a texture centred and scaled to fit,
 * and forwards mouse and keyboard input to the server as ComputerCraft events.
 */
public abstract class GuiScreenBase extends GuiScreen {
	private static final int BORDER = 3;

	/** The texture to display, or null to show nothing. */
	protected abstract Texture screenTexture();

	/** The monitor tile that receives input, or null when input should be ignored. */
	protected abstract TileEntityMonitor inputTarget();

	protected double scale = 1;
	protected int originX, originY;
	protected int screenW, screenH;
	private boolean mouseDown;
	private int mouseButton;
	private int lastPx = Integer.MIN_VALUE, lastPy = Integer.MIN_VALUE;

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		if (mouseDown) {
			mouseDown = false;
			TileEntityMonitor t = inputTarget();
			if (t != null) PacketSenders.mouseUp(t);
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private void layout(Texture tex) {
		screenW = tex.getWidth();
		screenH = tex.getHeight();
		double sx = (double) (width - 2 * BORDER - 4) / screenW;
		double sy = (double) (height - 2 * BORDER - 4) / screenH;
		scale = Math.min(1.0, Math.min(sx, sy));
		originX = (int) Math.round((width - screenW * scale) / 2);
		originY = (int) Math.round((height - screenH * scale) / 2);
	}

	/** Converts GUI coordinates to screen pixels; null when outside the screen. */
	protected int[] toPixel(int mx, int my) {
		if (screenW <= 0) return null;
		int px = (int) Math.floor((mx - originX) / scale);
		int py = (int) Math.floor((my - originY) / scale);
		if (px < 0 || py < 0 || px >= screenW || py >= screenH) return null;
		return new int[] { px, py };
	}

	@Override
	public void drawScreen(int mx, int my, float partialTicks) {
		drawDefaultBackground();
		Texture tex = screenTexture();
		if (tex == null) return;
		layout(tex);

		int w = (int) Math.round(screenW * scale);
		int h = (int) Math.round(screenH * scale);
		drawRect(originX - BORDER, originY - BORDER, originX + w + BORDER, originY + h + BORDER, 0xFF202020);
		drawRect(originX - 1, originY - 1, originX + w + 1, originY + h + 1, 0xFF000000);

		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_BLEND);
		TextureCache.bind(tex);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(originX, originY + h, zLevel, 0, 1);
		t.addVertexWithUV(originX + w, originY + h, zLevel, 1, 1);
		t.addVertexWithUV(originX + w, originY, zLevel, 1, 0);
		t.addVertexWithUV(originX, originY, zLevel, 0, 0);
		t.draw();

		if (mouseDown) {
			TileEntityMonitor target = inputTarget();
			int[] p = toPixel(mx, my);
			if (target == null) {
				mouseDown = false;
			} else if (p == null) {
				mouseDown = false;
				PacketSenders.mouseUp(target);
			} else if (p[0] != lastPx || p[1] != lastPy) {
				lastPx = p[0];
				lastPy = p[1];
				PacketSenders.mouseMove(p[0], p[1], target);
			}
		}
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			TileEntityMonitor target = inputTarget();
			if (target == null) return;
			int mx = Mouse.getEventX() * width / mc.displayWidth;
			int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
			int[] p = toPixel(mx, my);
			if (p != null) PacketSenders.scroll(p[0], p[1], wheel > 0 ? -1 : 1, target);
		}
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) {
		super.mouseClicked(mx, my, button);
		TileEntityMonitor target = inputTarget();
		int[] p = toPixel(mx, my);
		if (target == null || p == null) return;
		mouseDown = true;
		mouseButton = button;
		lastPx = p[0];
		lastPy = p[1];
		PacketSenders.mouseDown(p[0], p[1], button, target);
	}

	@Override
	protected void mouseMovedOrUp(int mx, int my, int button) {
		super.mouseMovedOrUp(mx, my, button);
		if (mouseDown && button == mouseButton) {
			mouseDown = false;
			TileEntityMonitor target = inputTarget();
			if (target != null) PacketSenders.mouseUp(target);
		}
	}

	@Override
	public void handleKeyboardInput() {
		super.handleKeyboardInput();
		if (!Keyboard.getEventKeyState()) {
			int key = Keyboard.getEventKey();
			TileEntityMonitor target = inputTarget();
			if (key != Keyboard.KEY_ESCAPE && key != Keyboard.KEY_NONE && target != null) {
				PacketSenders.keyUp(key, target);
			}
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		super.keyTyped(c, key);
		if (key == Keyboard.KEY_ESCAPE) return;
		TileEntityMonitor target = inputTarget();
		if (target == null) return;
		if (key != Keyboard.KEY_NONE) PacketSenders.keyDown(key, Keyboard.isRepeatEvent(), target);
		if (ChatAllowedCharacters.isAllowedCharacter(c)) PacketSenders.charTyped(c, target);
	}
}
