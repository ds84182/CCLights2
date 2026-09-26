package ds.mods.CCLights2.gpu;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.imageio.ImageIO;

import dan200.computercraft.api.lua.LuaException;
import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.gpu.shader.ShaderException;

/**
 * The graphics processor: a texture table, a draw state and the interpreter for draw commands.
 * The same code runs on the server (authoritative) and on every client (replica), so
 * {@link #processCommand} must be deterministic given the same command stream.
 */
public class GPU {
	public static final int MAX_TEXTURES = 4096;
	public static final int MAX_SHADERS = 64;
	/** Instruction budget per pixel per runShader call (configurable). */
	public static int shaderMaxOps = 20000;
	/** Largest region one runShader call may cover (configurable). */
	public static int shaderMaxPixels = 512 * 288;
	public static int shaderMaxSource = 64 * 1024;

	/** Texture slots. Slot 0 is always the current monitor's screen. */
	public final Texture[] textures = new Texture[MAX_TEXTURES];
	/** Memory budget in the same virtual units as {@link Texture#getMemoryUse()}. */
	public int maxmem;
	/** Commands executed since the last network flush (server only). */
	public final Deque<DrawCMD> drawlist = new ArrayDeque<DrawCMD>();
	public Texture bindedTexture;
	public int bindedSlot;
	public final List<Monitor> monitors = new ArrayList<Monitor>();
	public Monitor currentMonitor;
	public TileEntityGPU tile;
	public final DrawState state = new DrawState();
	/** Compiled shaders; ids are 1-based like textures. */
	public final ShaderInstance[] shaders = new ShaderInstance[MAX_SHADERS];
	/** True on the authoritative side; the server fills in allocated ids so clients reuse them. */
	public boolean server = true;

	public GPU(int gfxmem) {
		maxmem = gfxmem;
	}

	// ------------------------------------------------------------------ monitors

	public Monitor getMonitor() {
		return currentMonitor;
	}

	public void addMonitor(Monitor mon) {
		if (!monitors.contains(mon)) monitors.add(mon);
		setMonitor(mon);
	}

	public void removeMonitor(Monitor mon) {
		monitors.remove(mon);
		if (currentMonitor == mon) {
			currentMonitor = monitors.isEmpty() ? null : monitors.get(0);
			textures[0] = currentMonitor == null ? null : currentMonitor.tex;
			if (bindedSlot == 0) bindedTexture = textures[0];
		}
	}

	public void setMonitor(Monitor mon) {
		if (!monitors.contains(mon)) monitors.add(mon);
		currentMonitor = mon;
		textures[0] = mon.tex;
		if (bindedSlot == 0 || bindedTexture == null) {
			bindedTexture = mon.tex;
			bindedSlot = 0;
		}
	}

	// ------------------------------------------------------------------ memory

	public int getUsedMemory() {
		int used = 0;
		for (int i = 1; i < textures.length; i++) {
			if (textures[i] != null) used += textures[i].getMemoryUse();
		}
		return used;
	}

	public int getFreeMemory() {
		return maxmem - getUsedMemory();
	}

	/** Ids of every allocated texture, including 0 when a monitor is attached. */
	public List<Integer> listTextures() {
		List<Integer> ids = new ArrayList<Integer>();
		for (int i = 0; i < textures.length; i++) {
			if (textures[i] != null) ids.add(i);
		}
		return ids;
	}

	// ------------------------------------------------------------------ texture table

	/** Returns the texture in a slot or throws a Lua error describing the problem. */
	public Texture texture(int id) throws LuaException {
		if (id < 0 || id >= textures.length) throw new LuaException("texture id " + id + " out of range");
		Texture t = textures[id];
		if (t == null) throw new LuaException(id == 0 ? "no monitor connected" : "texture " + id + " does not exist");
		return t;
	}

	private int freeSlot() {
		for (int i = 1; i < textures.length; i++) {
			if (textures[i] == null) return i;
		}
		return -1;
	}

	/** Allocates a slot for a w x h texture, honouring the memory budget on the server. */
	private int allocate(int w, int h, int requestedSlot) throws LuaException {
		if (w < 1 || h < 1 || w > Texture.MAX_DIMENSION || h > Texture.MAX_DIMENSION)
			throw new LuaException("texture size must be between 1x1 and " + Texture.MAX_DIMENSION + "x" + Texture.MAX_DIMENSION);
		int slot = requestedSlot;
		if (slot <= 0) {
			if (server && Texture.memoryUseFor(w, h) > getFreeMemory()) throw new LuaException("not enough GPU memory (need " + Texture.memoryUseFor(w, h) + ", have " + getFreeMemory() + ")");
			slot = freeSlot();
			if (slot < 0) throw new LuaException("no free texture slots");
		} else if (slot >= textures.length) {
			throw new LuaException("texture id " + slot + " out of range");
		}
		if (textures[slot] != null) textures[slot].dispose();
		textures[slot] = new Texture(w, h);
		return slot;
	}

	public void bindTexture(int id) throws LuaException {
		bindedTexture = texture(id);
		bindedSlot = id;
	}

	public void freeTexture(int id) throws LuaException {
		if (id == 0) throw new LuaException("the monitor texture cannot be freed");
		Texture t = texture(id);
		if (bindedTexture == t) {
			bindedTexture = textures[0];
			bindedSlot = 0;
		}
		textures[id] = null;
		t.dispose();
	}

	/** Frees every texture except the monitor, frees every shader and resets the draw state. */
	public void freeAll() {
		for (int i = 1; i < textures.length; i++) {
			if (textures[i] != null) {
				textures[i].dispose();
				textures[i] = null;
			}
		}
		bindedTexture = textures[0];
		bindedSlot = 0;
		state.reset();
		for (int i = 0; i < shaders.length; i++) shaders[i] = null;
	}

	/** {@link #freeAll()} plus dropping any queued draw commands; used when reloading state. */
	public void reset() {
		freeAll();
		drawlist.clear();
	}

	// ------------------------------------------------------------------ shaders

	public ShaderInstance shader(int id) throws LuaException {
		if (id < 1 || id >= shaders.length || shaders[id] == null) throw new LuaException("shader " + id + " does not exist");
		return shaders[id];
	}

	public List<Integer> listShaders() {
		List<Integer> ids = new ArrayList<Integer>();
		for (int i = 1; i < shaders.length; i++) if (shaders[i] != null) ids.add(i);
		return ids;
	}

	/** Compiles a shader into a slot (the requested one, or the first free). */
	public int createShader(String source, int requestedSlot) throws LuaException {
		if (source == null || source.length() > shaderMaxSource) throw new LuaException("shader source too large (limit " + shaderMaxSource + " bytes)");
		int slot = requestedSlot;
		if (slot <= 0) {
			slot = -1;
			for (int i = 1; i < shaders.length; i++) {
				if (shaders[i] == null) {
					slot = i;
					break;
				}
			}
			if (slot < 0) throw new LuaException("no free shader slots (max " + (MAX_SHADERS - 1) + ")");
		} else if (slot >= shaders.length) {
			throw new LuaException("shader id out of range");
		}
		try {
			shaders[slot] = new ShaderInstance(source);
		} catch (ShaderException e) {
			throw new LuaException("shader compile error: " + e.getMessage());
		} catch (RuntimeException e) {
			throw new LuaException("shader compile error: " + e);
		}
		return slot;
	}

	/** Pixel snapshots of the textures bound to a shader's samplers, so rendering can run unlocked. */
	public int[][] snapshotSamplers(int[] samplerIds, int[] outW, int[] outH) {
		int[][] px = new int[samplerIds.length][];
		for (int i = 0; i < samplerIds.length; i++) {
			int id = samplerIds[i];
			Texture t = id >= 0 && id < textures.length ? textures[id] : null;
			if (t == null || t.isDisposed()) continue;
			px[i] = t.getPixels(0, 0, t.getWidth(), t.getHeight());
			outW[i] = t.getWidth();
			outH[i] = t.getHeight();
		}
		return px;
	}

	/** Runs a shader over a w x h region; returns ARGB pixels. Safe to call without the GPU lock. */
	public static int[] renderShader(ShaderInstance sh, float[] values, int[][] samplerPx, int[] samplerW, int[] samplerH, int w, int h) throws LuaException {
		if (w < 1 || h < 1) throw new LuaException("shader region must be at least 1x1");
		if ((long) w * h > shaderMaxPixels) throw new LuaException("shader region too large: " + w + "x" + h + " exceeds " + shaderMaxPixels + " pixels");
		int[] out = new int[w * h];
		try {
			sh.program.render(values, samplerPx, samplerW, samplerH, w, h, out, shaderMaxOps);
		} catch (ShaderException e) {
			throw new LuaException("shader runtime error: " + e.getMessage());
		}
		return out;
	}

	public BufferedImage loadImage(byte[] data) throws LuaException {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null) throw new LuaException("unsupported or corrupt image data");
			return img;
		} catch (IOException e) {
			throw new LuaException("failed to decode image: " + e.getMessage());
		}
	}

	private Texture bound() throws LuaException {
		if (bindedTexture == null || bindedTexture.isDisposed()) {
			if (textures[0] != null) {
				bindedTexture = textures[0];
				bindedSlot = 0;
			} else {
				throw new LuaException("no texture bound: connect a monitor or bind a texture");
			}
		}
		return bindedTexture;
	}

	// ------------------------------------------------------------------ command interpreter

	/**
	 * Executes one command against this GPU. For allocating commands the server appends the
	 * chosen id to the arguments so the replicated command lands in the same slot on clients.
	 * @return values to hand back to Lua, or null
	 */
	public Object[] processCommand(DrawCMD cmd) throws LuaException {
		if (cmd == null || cmd.cmd == null) return null;
		DrawState s = state;
		switch (cmd.cmd) {
		case SetColor:
			s.color = new Color(clamp(cmd.getInt(0)), clamp(cmd.getInt(1)), clamp(cmd.getInt(2)), cmd.args.length > 3 ? clamp(cmd.getInt(3)) : 255);
			return null;
		case SetLineWidth:
			s.lineWidth = (float) Math.max(0.1, Math.min(256, cmd.getDouble(0)));
			return null;
		case SetBlendMode: {
			BlendComposite b = BlendComposite.byName(cmd.getString(0));
			if (b == null) throw new LuaException("unknown blend mode '" + cmd.getString(0) + "' (expected one of: " + BlendComposite.names() + ")");
			s.blend = b;
			return null;
		}
		case SetAntialias:
			s.antialias = cmd.getBool(0);
			return null;
		case SetClip:
			s.clip = new Rectangle(cmd.getInt(0), cmd.getInt(1), Math.max(0, cmd.getInt(2)), Math.max(0, cmd.getInt(3)));
			return null;
		case ResetClip:
			s.clip = null;
			return null;
		case Translate:
			s.transform.translate(cmd.getDouble(0), cmd.getDouble(1));
			return null;
		case Rotate:
			s.transform.rotate(cmd.getDouble(0));
			return null;
		case RotateAround:
			s.transform.rotate(cmd.getDouble(0), cmd.getDouble(1), cmd.getDouble(2));
			return null;
		case Scale:
			s.transform.scale(cmd.getDouble(0), cmd.getDouble(1));
			return null;
		case Shear:
			s.transform.shear(cmd.getDouble(0), cmd.getDouble(1));
			return null;
		case Push:
			if (!s.push()) throw new LuaException("transform stack overflow (max " + DrawState.MAX_STACK + ")");
			return null;
		case Pop:
			if (!s.pop()) throw new LuaException("transform stack underflow: pop without push");
			return null;
		case Origin:
			s.transform.setToIdentity();
			return null;

		case Fill:
			bound().fill(s.color);
			return null;
		case ClearRectangle:
			bound().clearRect(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3));
			return null;
		case Plot:
			bound().plot(s, cmd.getInt(0), cmd.getInt(1));
			return null;
		case Line:
			bound().line(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3));
			return null;
		case Rectangle:
			bound().rect(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3));
			return null;
		case FilledRectangle:
			bound().filledRect(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3));
			return null;
		case RoundRectangle:
			bound().roundRect(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3), cmd.getInt(4), cmd.getInt(5));
			return null;
		case FilledRoundRectangle:
			bound().filledRoundRect(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3), cmd.getInt(4), cmd.getInt(5));
			return null;
		case Triangle:
			bound().polygon(s, new int[] { cmd.getInt(0), cmd.getInt(2), cmd.getInt(4) }, new int[] { cmd.getInt(1), cmd.getInt(3), cmd.getInt(5) }, 3);
			return null;
		case FilledTriangle:
			bound().filledPolygon(s, new int[] { cmd.getInt(0), cmd.getInt(2), cmd.getInt(4) }, new int[] { cmd.getInt(1), cmd.getInt(3), cmd.getInt(5) }, 3);
			return null;
		case Oval:
			bound().oval(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3));
			return null;
		case FilledOval:
			bound().filledOval(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3));
			return null;
		case Arc:
			bound().arc(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3), cmd.getInt(4), cmd.getInt(5));
			return null;
		case FilledArc:
			bound().filledArc(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3), cmd.getInt(4), cmd.getInt(5));
			return null;
		case Polygon:
		case FilledPolygon: {
			int[] pts = (int[]) cmd.args[0];
			int n = pts.length / 2;
			int[] xs = new int[n], ys = new int[n];
			for (int i = 0; i < n; i++) {
				xs[i] = pts[i * 2];
				ys[i] = pts[i * 2 + 1];
			}
			if (cmd.cmd == CommandEnum.Polygon) bound().polygon(s, xs, ys, n);
			else bound().filledPolygon(s, xs, ys, n);
			return null;
		}
		case Curve:
			bound().curve(s, cmd.getDouble(0), cmd.getDouble(1), cmd.getDouble(2), cmd.getDouble(3), cmd.getDouble(4), cmd.getDouble(5));
			return null;
		case Bezier:
			bound().bezier(s, cmd.getDouble(0), cmd.getDouble(1), cmd.getDouble(2), cmd.getDouble(3), cmd.getDouble(4), cmd.getDouble(5), cmd.getDouble(6), cmd.getDouble(7));
			return null;
		case GradientRectangle:
			bound().gradientRect(s, cmd.getInt(0), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3),
					new Color(clamp(cmd.getInt(4)), clamp(cmd.getInt(5)), clamp(cmd.getInt(6)), clamp(cmd.getInt(7))), cmd.getBool(8));
			return null;
		case DrawText:
			bound().drawText(s, cmd.getString(0), cmd.getInt(1), cmd.getInt(2));
			return null;
		case DrawTexture: {
			Texture src = texture(cmd.getInt(0));
			if (cmd.args.length >= 7) {
				bound().drawTexture(s, src, cmd.getInt(1), cmd.getInt(2), cmd.getInt(3), cmd.getInt(4), cmd.getInt(5), cmd.getInt(6));
			} else {
				bound().drawTexture(s, src, cmd.getInt(1), cmd.getInt(2));
			}
			return null;
		}
		case DrawTextureScaled:
			bound().drawTextureScaled(s, texture(cmd.getInt(0)), cmd.getInt(1), cmd.getInt(2), cmd.getInt(3), cmd.getInt(4));
			return null;
		case SetPixels: {
			// args: w, h, x, y, int[] argb
			int w = cmd.getInt(0), h = cmd.getInt(1), x = cmd.getInt(2), y = cmd.getInt(3);
			bound().setPixels(x, y, w, h, (int[]) cmd.args[4]);
			return null;
		}

		case CreateShader: {
			int id = createShader(cmd.getString(0), cmd.args.length > 1 ? cmd.getInt(1) : -1);
			cmd.args = new Object[] { cmd.args[0], id };
			return new Object[] { id };
		}
		case FreeShader: {
			int id = cmd.getInt(0);
			shader(id);
			shaders[id] = null;
			return null;
		}
		case Reset:
			freeAll();
			return null;
		case RunShader: {
			// args: id, x, y, w, h, float[] uniform values, int[] sampler texture ids
			ShaderInstance sh = shader(cmd.getInt(0));
			int x = cmd.getInt(1), y = cmd.getInt(2), w = cmd.getInt(3), h = cmd.getInt(4);
			float[] values = (float[]) cmd.args[5];
			int[] samplerIds = (int[]) cmd.args[6];
			if (values.length == sh.values.length) System.arraycopy(values, 0, sh.values, 0, values.length);
			System.arraycopy(samplerIds, 0, sh.samplers, 0, Math.min(samplerIds.length, sh.samplers.length));
			int[] sw = new int[samplerIds.length], shh = new int[samplerIds.length];
			int[][] px = snapshotSamplers(samplerIds, sw, shh);
			int[] out = renderShader(sh, values, px, sw, shh, w, h);
			bound().drawPixels(s, out, x, y, w, h);
			return null;
		}
		case CreateTexture: {
			int id = allocate(cmd.getInt(0), cmd.getInt(1), cmd.args.length > 2 ? cmd.getInt(2) : -1);
			cmd.args = new Object[] { cmd.args[0], cmd.args[1], id };
			return new Object[] { id };
		}
		case Import: {
			BufferedImage img = loadImage((byte[]) cmd.args[0]);
			int id = allocate(img.getWidth(), img.getHeight(), cmd.args.length > 1 ? cmd.getInt(1) : -1);
			textures[id].getGraphics().drawImage(img, 0, 0, null);
			textures[id].markDirty();
			cmd.args = new Object[] { cmd.args[0], id };
			return new Object[] { id, img.getWidth(), img.getHeight() };
		}
		case CopyTexture: {
			Texture src = texture(cmd.getInt(0));
			int id = allocate(src.getWidth(), src.getHeight(), cmd.args.length > 1 ? cmd.getInt(1) : -1);
			textures[id].copyFrom(src);
			cmd.args = new Object[] { cmd.args[0], id };
			return new Object[] { id };
		}
		case BindTexture:
			bindTexture(cmd.getInt(0));
			return null;
		case FreeTexture:
			freeTexture(cmd.getInt(0));
			return null;
		case ResizeTexture: {
			int id = cmd.getInt(0);
			if (id == 0) throw new LuaException("the monitor texture cannot be resized");
			Texture t = texture(id);
			int w = cmd.getInt(1), h = cmd.getInt(2);
			if (w < 1 || h < 1 || w > Texture.MAX_DIMENSION || h > Texture.MAX_DIMENSION) throw new LuaException("invalid texture size");
			if (server && Texture.memoryUseFor(w, h) - t.getMemoryUse() > getFreeMemory()) throw new LuaException("not enough GPU memory");
			t.resize(w, h, cmd.getBool(3));
			return null;
		}
		case FlipVertically:
			texture(cmd.getInt(0)).flipVertical();
			return null;
		case FlipHorizontally:
			texture(cmd.getInt(0)).flipHorizontal();
			return null;
		case Blur:
			texture(cmd.getInt(0)).blur(cmd.args.length > 1 ? cmd.getInt(1) : 2);
			return null;
		case GaussianBlur:
			texture(cmd.getInt(0)).gaussianBlur((float) cmd.getDouble(1));
			return null;
		case Glow:
			texture(cmd.getInt(0)).glow((float) cmd.getDouble(1));
			return null;
		case Sharpen:
			texture(cmd.getInt(0)).sharpen((float) cmd.getDouble(1));
			return null;
		case Invert:
			texture(cmd.getInt(0)).invert();
			return null;
		case Grayscale:
			texture(cmd.getInt(0)).grayscale();
			return null;
		default:
			return null;
		}
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}

	/** Publishes changed monitor pixels to the render side. */
	public void updateMonitors() {
		for (Monitor m : monitors) m.tex.texUpdate();
	}
}
