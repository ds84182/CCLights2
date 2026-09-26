package ds.mods.CCLights2.gpu;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import dan200.computercraft.api.lua.LuaException;
import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.gpu.shader.Program.Uniform;
import ds.mods.CCLights2.gpu.shader.ShaderException;

/**
 * The Lua-facing GPU method table: validates arguments, runs commands on the GPU and records
 * the replicated ones. Has no Minecraft dependencies so it can be driven from tests.
 */
public final class GpuLuaApi {
	/** What the peripheral host provides: frame batching state and file access for gpu.import(). */
	public interface Host {
		void setFrame(boolean on);
	}

	/** Resolves a file name passed to gpu.import() against the calling computer's folder. */
	public interface ImportSource {
		byte[] read(String method, String fileName) throws LuaException;
	}

	/** Lua method table: index -> command. Aliases are appended after the canonical names. */
	public static final String[] METHOD_NAMES;
	public static final CommandEnum[] METHOD_COMMANDS;
	static {
		List<String> names = new ArrayList<String>();
		List<CommandEnum> cmds = new ArrayList<CommandEnum>();
		for (CommandEnum c : CommandEnum.VALUES) {
			names.add(c.luaName);
			cmds.add(c);
		}
		for (String[] alias : CommandEnum.ALIASES) {
			for (CommandEnum c : CommandEnum.VALUES) {
				if (c.luaName.equals(alias[1])) {
					names.add(alias[0]);
					cmds.add(c);
				}
			}
		}
		METHOD_NAMES = names.toArray(new String[0]);
		METHOD_COMMANDS = cmds.toArray(new CommandEnum[0]);
	}

	private final GPU gpu;
	private final Host host;

	public GpuLuaApi(GPU gpu, Host host) {
		this.gpu = gpu;
		this.host = host;
	}

	public static int methodCount() {
		return METHOD_NAMES.length;
	}

	/** Index of a Lua method name, or -1. */
	public static int methodIndex(String name) {
		for (int i = 0; i < METHOD_NAMES.length; i++) if (METHOD_NAMES[i].equals(name)) return i;
		return -1;
	}

	/**
	 * Calls method number {@code method} with ComputerCraft-style arguments. Locks the GPU for
	 * everything except runShader, which renders unlocked and only locks to blit.
	 */
	public Object[] call(int method, Object[] args, ImportSource source) throws LuaException {
		if (method < 0 || method >= METHOD_COMMANDS.length) throw new LuaException("no such method");
		if (args == null) args = DrawCMD.NO_ARGS;
		CommandEnum cmd = METHOD_COMMANDS[method];
		String name = METHOD_NAMES[method];
		if (cmd == CommandEnum.RunShader) return runShader(args, name);
		synchronized (gpu) {
			return dispatch(cmd, name, args, source);
		}
	}

	/** Executes a command and, when replicated, records it for the next network flush. */
	private Object[] run(CommandEnum cmd, Object... args) throws LuaException {
		DrawCMD c = new DrawCMD(cmd, args);
		Object[] ret = gpu.processCommand(c);
		if (cmd.replicated) gpu.drawlist.add(c);
		return ret;
	}

	private Object[] runInts(CommandEnum cmd, Object[] args, int count, String name) throws LuaException {
		return run(cmd, LuaArgs.box(LuaArgs.getInts(args, 0, count, name)));
	}

	private Object[] dispatch(CommandEnum cmd, String name, Object[] args, ImportSource source) throws LuaException {
		switch (cmd) {
		// ---- state
		case SetColor: {
			int r = LuaArgs.clampColor(LuaArgs.getInt(args, 0, name));
			int g = LuaArgs.clampColor(LuaArgs.getInt(args, 1, name));
			int b = LuaArgs.clampColor(LuaArgs.getInt(args, 2, name));
			int a = LuaArgs.clampColor(LuaArgs.optInt(args, 3, name, 255));
			java.awt.Color cur = gpu.state.color;
			if (cur.getRed() == r && cur.getGreen() == g && cur.getBlue() == b && cur.getAlpha() == a) return null;
			return run(cmd, r, g, b, a);
		}
		case GetColor: {
			java.awt.Color c = gpu.state.color;
			return new Object[] { c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() };
		}
		case SetLineWidth:
			return run(cmd, LuaArgs.getDouble(args, 0, name));
		case GetLineWidth:
			return new Object[] { (double) gpu.state.lineWidth };
		case SetBlendMode: {
			String mode = LuaArgs.getString(args, 0, name);
			if (BlendComposite.byName(mode) == null) throw new LuaException("bad argument #1 to '" + name + "' (unknown blend mode '" + mode + "', expected one of: " + BlendComposite.names() + ")");
			return run(cmd, mode);
		}
		case GetBlendMode:
			return new Object[] { gpu.state.blend.luaName };
		case SetAntialias:
			return run(cmd, LuaArgs.optBool(args, 0, name, true));
		case GetAntialias:
			return new Object[] { gpu.state.antialias };
		case SetClip:
			return runInts(cmd, args, 4, name);
		case ResetClip:
			return run(cmd);
		case Reset:
			return run(cmd);
		case GetClip:
			if (gpu.state.clip == null) return null;
			return new Object[] { gpu.state.clip.x, gpu.state.clip.y, gpu.state.clip.width, gpu.state.clip.height };
		case Translate:
			return run(cmd, LuaArgs.getDouble(args, 0, name), LuaArgs.getDouble(args, 1, name));
		case Rotate:
			return run(cmd, LuaArgs.getDouble(args, 0, name));
		case RotateAround:
			return run(cmd, LuaArgs.getDouble(args, 0, name), LuaArgs.getDouble(args, 1, name), LuaArgs.getDouble(args, 2, name));
		case Scale: {
			double sx = LuaArgs.getDouble(args, 0, name);
			double sy = LuaArgs.optDouble(args, 1, name, sx);
			return run(cmd, sx, sy);
		}
		case Shear:
			return run(cmd, LuaArgs.getDouble(args, 0, name), LuaArgs.getDouble(args, 1, name));
		case Push:
		case Pop:
		case Origin:
		case Fill:
			return run(cmd);

		// ---- primitives
		case Plot:
			return runInts(cmd, args, 2, name);
		case Line:
		case Rectangle:
		case FilledRectangle:
		case ClearRectangle:
			return runInts(cmd, args, 4, name);
		case Oval:
		case FilledOval: {
			int[] v = LuaArgs.getInts(args, 0, 4, name);
			return run(cmd, LuaArgs.box(v));
		}
		case RoundRectangle:
		case FilledRoundRectangle: {
			int[] v = LuaArgs.getInts(args, 0, 4, name);
			int aw = LuaArgs.optInt(args, 4, name, 4);
			int ah = LuaArgs.optInt(args, 5, name, aw);
			return run(cmd, v[0], v[1], v[2], v[3], aw, ah);
		}
		case Triangle:
		case FilledTriangle:
		case Arc:
		case FilledArc:
			return runInts(cmd, args, 6, name);
		case Polygon:
		case FilledPolygon: {
			int[] pts;
			if (args.length == 1 && args[0] instanceof Map) {
				pts = LuaArgs.tableToInts(LuaArgs.getTable(args, 0, name), 0, name);
			} else {
				pts = LuaArgs.getInts(args, 0, args.length, name);
			}
			if (pts.length < 6 || pts.length % 2 != 0) throw new LuaException(name + ": expected at least three x,y pairs");
			return run(cmd, (Object) pts);
		}
		case Curve: {
			Object[] v = new Object[6];
			for (int i = 0; i < 6; i++) v[i] = LuaArgs.getDouble(args, i, name);
			return run(cmd, v);
		}
		case Bezier: {
			Object[] v = new Object[8];
			for (int i = 0; i < 8; i++) v[i] = LuaArgs.getDouble(args, i, name);
			return run(cmd, v);
		}
		case GradientRectangle: {
			int[] r = LuaArgs.getInts(args, 0, 4, name);
			int r2 = LuaArgs.clampColor(LuaArgs.getInt(args, 4, name));
			int g2 = LuaArgs.clampColor(LuaArgs.getInt(args, 5, name));
			int b2 = LuaArgs.clampColor(LuaArgs.getInt(args, 6, name));
			int a2 = LuaArgs.clampColor(LuaArgs.optInt(args, 7, name, 255));
			boolean vertical = LuaArgs.optBool(args, 8, name, false);
			return run(cmd, r[0], r[1], r[2], r[3], r2, g2, b2, a2, vertical);
		}
		case DrawText: {
			String text = LuaArgs.getString(args, 0, name);
			int x = LuaArgs.getInt(args, 1, name);
			int y = LuaArgs.getInt(args, 2, name);
			if (text.isEmpty()) return null;
			return run(cmd, text, x, y);
		}
		case GetTextWidth:
			return new Object[] { Texture.getStringWidth(LuaArgs.getString(args, 0, name)) };
		case GetTextHeight:
			return new Object[] { Texture.FONT_HEIGHT };
		case DrawTexture: {
			if (args.length >= 7) return runInts(cmd, args, 7, name);
			return runInts(cmd, args, 3, name);
		}
		case DrawTextureScaled:
			return runInts(cmd, args, 5, name);
		case SetPixels: {
			int w = LuaArgs.getInt(args, 0, name);
			int h = LuaArgs.getInt(args, 1, name);
			int x = LuaArgs.getInt(args, 2, name);
			int y = LuaArgs.getInt(args, 3, name);
			if (w < 0 || h < 0 || (long) w * h > Texture.MAX_DIMENSION * Texture.MAX_DIMENSION) throw new LuaException(name + ": invalid size");
			int[] raw = LuaArgs.tableToInts(LuaArgs.getTable(args, 4, name), 4, name);
			int n = w * h;
			int stride;
			if (raw.length >= n * 4) stride = 4;
			else if (raw.length >= n * 3) stride = 3;
			else throw new LuaException(name + ": expected " + (n * 4) + " colour values (r,g,b,a per pixel), got " + raw.length);
			int[] argb = new int[n];
			for (int i = 0; i < n; i++) {
				int r = LuaArgs.clampColor(raw[i * stride]);
				int g = LuaArgs.clampColor(raw[i * stride + 1]);
				int b = LuaArgs.clampColor(raw[i * stride + 2]);
				int a = stride == 4 ? LuaArgs.clampColor(raw[i * stride + 3]) : 255;
				argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
			}
			return run(cmd, w, h, x, y, argb);
		}
		case GetPixels: {
			int x = LuaArgs.getInt(args, 0, name);
			int y = LuaArgs.getInt(args, 1, name);
			Texture t = gpu.texture(gpu.bindedSlot);
			if (args.length < 4) {
				int p = t.getRGB(x, y);
				return new Object[] { (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, p >>> 24 };
			}
			int w = LuaArgs.getInt(args, 2, name);
			int h = LuaArgs.getInt(args, 3, name);
			if (w < 0 || h < 0 || (long) w * h > 1 << 20) throw new LuaException(name + ": region too large");
			int[] px = t.getPixels(x, y, w, h);
			Map<Double, Double> out = new HashMap<Double, Double>(px.length * 4);
			double k = 1;
			for (int p : px) {
				out.put(k++, (double) ((p >> 16) & 0xFF));
				out.put(k++, (double) ((p >> 8) & 0xFF));
				out.put(k++, (double) (p & 0xFF));
				out.put(k++, (double) (p >>> 24));
			}
			return new Object[] { out };
		}

		// ---- textures
		case CreateTexture:
			return runInts(cmd, args, 2, name);
		case BindTexture:
			return runInts(cmd, args, 1, name);
		case FreeTexture:
			return runInts(cmd, args, 1, name);
		case FlipVertically:
		case FlipHorizontally:
		case Invert:
		case Grayscale:
			return runInts(cmd, args, 1, name);
		case CopyTexture:
			return runInts(cmd, args, 1, name);
		case ResizeTexture: {
			int[] v = LuaArgs.getInts(args, 0, 3, name);
			return run(cmd, v[0], v[1], v[2], LuaArgs.optBool(args, 3, name, false));
		}
		case Blur:
			return run(cmd, LuaArgs.getInt(args, 0, name), LuaArgs.optInt(args, 1, name, 2));
		case GaussianBlur:
			return run(cmd, LuaArgs.getInt(args, 0, name), LuaArgs.optDouble(args, 1, name, 2));
		case Glow:
			return run(cmd, LuaArgs.getInt(args, 0, name), LuaArgs.optDouble(args, 1, name, 0.5));
		case Sharpen:
			return run(cmd, LuaArgs.getInt(args, 0, name), LuaArgs.optDouble(args, 1, name, 0.5));
		case Import:
			return run(cmd, (Object) importData(args, name, source));
		case Export: {
			int id = LuaArgs.getInt(args, 0, name);
			String format = LuaArgs.getString(args, 1, name).toLowerCase();
			if (!format.equals("png") && !format.equals("jpg") && !format.equals("jpeg") && !format.equals("bmp") && !format.equals("gif"))
				throw new LuaException(name + ": format must be png, jpg, bmp or gif");
			Texture t = gpu.texture(id);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				BufferedImage img = t.getImage();
				if (format.equals("jpg") || format.equals("jpeg") || format.equals("bmp")) {
					// These formats have no alpha channel.
					BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
					rgb.getGraphics().drawImage(img, 0, 0, null);
					img = rgb;
				}
				ImageIO.write(img, format, out);
			} catch (IOException e) {
				throw new LuaException(name + ": " + e.getMessage());
			}
			byte[] data = out.toByteArray();
			Map<Double, Double> table = new HashMap<Double, Double>(data.length * 2);
			for (int i = 0; i < data.length; i++) table.put((double) (i + 1), (double) (data[i] & 0xFF));
			return new Object[] { table };
		}
		case GetSize: {
			int id = LuaArgs.optInt(args, 0, name, gpu.bindedSlot);
			Texture t = gpu.texture(id);
			return new Object[] { t.getWidth(), t.getHeight() };
		}
		case GetBindedTexture:
			return new Object[] { gpu.bindedSlot };
		case ListTextures: {
			Map<Double, Double> table = new HashMap<Double, Double>();
			double k = 1;
			for (int id : gpu.listTextures()) table.put(k++, (double) id);
			return new Object[] { table };
		}
		case GetFreeMemory:
			return new Object[] { gpu.getFreeMemory() };
		case GetTotalMemory:
			return new Object[] { gpu.maxmem };
		case GetUsedMemory:
			return new Object[] { gpu.getUsedMemory() };
		case GetMonitor:
			return gpu.currentMonitor == null ? null : new Object[] { gpu.currentMonitor.obj };
		case CreateShader:
		case FreeShader:
		case SetUniform:
		case GetShaderUniforms:
		case ListShaders:
			return shaderMethod(cmd, name, args);
		case StartFrame:
			host.setFrame(true);
			return null;
		case EndFrame:
			host.setFrame(false);
			return null;
		default:
			throw new LuaException(name + " is not implemented");
		}
	}

	/**
	 * runShader renders outside the GPU lock so a slow shader on the ComputerCraft thread
	 * cannot stall the server tick; only the final blit takes the lock.
	 */
	private Object[] runShader(Object[] args, String name) throws LuaException {
		int id = LuaArgs.getInt(args, 0, name);
		ShaderInstance sh;
		int x, y, w, h;
		float[] values;
		int[] samplerIds;
		int[] sw, shh;
		int[][] px;
		synchronized (gpu) {
			sh = gpu.shader(id);
			Texture target = gpu.texture(gpu.bindedSlot);
			x = LuaArgs.optInt(args, 1, name, 0);
			y = LuaArgs.optInt(args, 2, name, 0);
			w = LuaArgs.optInt(args, 3, name, target.getWidth() - x);
			h = LuaArgs.optInt(args, 4, name, target.getHeight() - y);
			if (w < 1 || h < 1) throw new LuaException(name + ": region must be at least 1x1");
			if ((long) w * h > GPU.shaderMaxPixels) throw new LuaException(name + ": region " + w + "x" + h + " exceeds the limit of " + GPU.shaderMaxPixels + " pixels");
			samplerIds = sh.samplers.clone();
			sw = new int[samplerIds.length];
			shh = new int[samplerIds.length];
			px = gpu.snapshotSamplers(samplerIds, sw, shh);
			sh.prepare(w, h, sw, shh);
			values = sh.values.clone();
		}
		int[] out = GPU.renderShader(sh, values, px, sw, shh, w, h);
		synchronized (gpu) {
			Texture target = gpu.texture(gpu.bindedSlot);
			target.drawPixels(gpu.state, out, x, y, w, h);
			gpu.drawlist.add(new DrawCMD(CommandEnum.RunShader, id, x, y, w, h, values, samplerIds));
		}
		return null;
	}

	private Object[] shaderMethod(CommandEnum cmd, String name, Object[] args) throws LuaException {
		switch (cmd) {
		case CreateShader: {
			String src = LuaArgs.getString(args, 0, name);
			return run(cmd, src);
		}
		case FreeShader:
			return runInts(cmd, args, 1, name);
		case SetUniform: {
			ShaderInstance sh = gpu.shader(LuaArgs.getInt(args, 0, name));
			String uname = LuaArgs.getString(args, 1, name);
			Uniform u = sh.program.uniform(uname);
			if (u == null) throw new LuaException(name + ": shader has no uniform named " + uname);
			try {
				if (u.isSampler()) {
					int tex = LuaArgs.getInt(args, 2, name);
					gpu.texture(tex);
					sh.setSampler(uname, tex);
				} else if (args.length > 2 && args[2] instanceof Map) {
					Map<?, ?> m = LuaArgs.getTable(args, 2, name);
					double[] v = new double[m.size()];
					for (int i = 0; i < v.length; i++) {
						Object o = m.get((double) (i + 1));
						if (!(o instanceof Number)) throw new LuaException(name + ": table entry " + (i + 1) + " is not a number");
						v[i] = ((Number) o).doubleValue();
					}
					sh.setUniform(uname, v);
				} else {
					if (args.length < 3) throw new LuaException(name + ": value expected");
					double[] v = new double[args.length - 2];
					for (int i = 0; i < v.length; i++) v[i] = LuaArgs.getDouble(args, i + 2, name);
					sh.setUniform(uname, v);
				}
			} catch (ShaderException e) {
				throw new LuaException(name + ": " + e.getMessage());
			}
			return null;
		}
		case GetShaderUniforms: {
			ShaderInstance sh = gpu.shader(LuaArgs.getInt(args, 0, name));
			Map<String, String> table = new HashMap<String, String>();
			for (Uniform u : sh.program.uniforms) table.put(u.name, u.type.toString());
			return new Object[] { table };
		}
		case ListShaders: {
			Map<Double, Double> table = new HashMap<Double, Double>();
			double k = 1;
			for (int id : gpu.listShaders()) table.put(k++, (double) id);
			return new Object[] { table };
		}
		default:
			throw new LuaException(name + " is not implemented");
		}
	}

	/** Resolves the argument of gpu.import(): a table of bytes, raw image data in a string, or a file name. */
	private byte[] importData(Object[] args, String name, ImportSource source) throws LuaException {
		if (args.length >= 1 && args[0] instanceof Map) {
			return LuaArgs.tableToBytes(LuaArgs.getTable(args, 0, name), 0, name);
		}
		if (args.length >= 1 && args[0] instanceof String) {
			String s = (String) args[0];
			if (looksLikeImage(s)) return LuaArgs.stringToBytes(s);
			if (s.isEmpty() || s.contains("..") || s.startsWith("/") || s.startsWith("\\") || s.contains(":"))
				throw new LuaException(name + ": invalid file name '" + s + "'");
			if (source == null) throw new LuaException(name + ": file import is not available here");
			return source.read(name, s);
		}
		throw new LuaException("bad argument #1 to '" + name + "' (table of bytes, image data or file name expected)");
	}

	private static boolean looksLikeImage(String s) {
		if (s.length() < 4) return false;
		int a = s.charAt(0), b = s.charAt(1), c = s.charAt(2), d = s.charAt(3);
		return (a == 0x89 && b == 'P' && c == 'N' && d == 'G')
				|| (a == 0xFF && b == 0xD8)
				|| (a == 'G' && b == 'I' && c == 'F')
				|| (a == 'B' && b == 'M');
	}

}
