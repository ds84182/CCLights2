package ds.mods.CCLights2.network;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.DrawState;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.Texture;

/**
 * Minecraft-free encoders and decoders for the NET_* message bodies, shared by {@link PacketSenders},
 * {@link PacketProcessor} and the client handlers, and exercised directly by the unit tests.
 * Every message starts with a {@link Target} (block position as {@code BlockPos.asLong()} plus the
 * dimension id string).
 */
public final class WireFormat {
	private WireFormat() {}

	/** Uncompressed size at which a draw list is cut into another message. */
	public static final int DRAWLIST_BATCH_BYTES = 1024 * 1024;
	/** Largest accepted draw list length (commands per message). */
	public static final int MAX_DRAWLIST = 1_000_000;
	/** Largest tablet screenshot accepted by the server. */
	public static final int MAX_SCREENSHOT = 2 * 1024 * 1024;
	/** Longest dimension id accepted. */
	public static final int MAX_DIMENSION_ID = 256;

	// ------------------------------------------------------------------ target

	/** Where a message is aimed: a block position ({@code BlockPos.asLong()}) in a dimension. */
	public record Target(long pos, String dimension) {}

	public static void writeTarget(ByteArrayDataOutput out, long pos, String dimension) {
		out.writeLong(pos);
		Serialize.writeString(out, dimension);
	}

	public static Target readTarget(ByteArrayDataInput in) throws IOException {
		long pos = in.readLong();
		int len = Serialize.checkLength(in, in.readInt(), 1);
		if (len > MAX_DIMENSION_ID) throw new IOException("dimension id too long");
		byte[] b = new byte[len];
		in.readFully(b);
		return new Target(pos, new String(b, java.nio.charset.StandardCharsets.UTF_8));
	}

	// ------------------------------------------------------------------ draw lists

	/**
	 * Encodes draw commands as one or more NET_GPUDRAWLIST payloads: {@code target, int n, n x command}
	 * (command = {@link Serialize#writeCommand}). A new payload starts once the current one passes
	 * {@code batchBytes}; a single command is never split. Commands whose arguments cannot be serialized
	 * are skipped with a warning (the replica would be out of step either way).
	 */
	public static List<byte[]> encodeDrawLists(long pos, String dimension, Iterable<DrawCMD> cmds, int batchBytes) {
		List<byte[]> out = new ArrayList<>();
		ByteArrayDataOutput body = ByteStreams.newDataOutput();
		int count = 0;
		int size = 0;
		for (DrawCMD c : cmds) {
			ByteArrayDataOutput one = ByteStreams.newDataOutput();
			try {
				Serialize.writeCommand(one, c);
			} catch (RuntimeException e) {
				CCLights2.LOGGER.warn("Cannot replicate GPU command " + c.cmd + ": " + e.getMessage());
				continue;
			}
			byte[] bytes = one.toByteArray();
			if (count > 0 && size + bytes.length > batchBytes) {
				out.add(finishDrawList(pos, dimension, count, body.toByteArray()));
				body = ByteStreams.newDataOutput();
				count = 0;
				size = 0;
			}
			body.write(bytes);
			size += bytes.length;
			count++;
		}
		if (count > 0) out.add(finishDrawList(pos, dimension, count, body.toByteArray()));
		return out;
	}

	private static byte[] finishDrawList(long pos, String dimension, int count, byte[] body) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput(body.length + 64);
		writeTarget(out, pos, dimension);
		out.writeInt(count);
		out.write(body);
		return out.toByteArray();
	}

	/** Reads the part of a NET_GPUDRAWLIST payload after the target. */
	public static List<DrawCMD> readDrawList(ByteArrayDataInput in) throws IOException {
		int n = Serialize.checkLength(in, in.readInt(), 8);
		if (n > MAX_DRAWLIST) throw new IOException("draw list too long: " + n);
		List<DrawCMD> cmds = new ArrayList<>(n);
		for (int i = 0; i < n; i++) cmds.add(Serialize.readCommand(in));
		return cmds;
	}

	// ------------------------------------------------------------------ GPU sync

	/** One texture in a sync message. */
	public record TextureData(int id, int width, int height, int[] argb) {}

	/** One shader in a sync message. */
	public record ShaderData(int id, String source, float[] values, int[] samplers) {}

	/** Decoded NET_GPUSYNC body. */
	public record GpuSnapshot(DrawState state, int boundSlot, List<TextureData> textures, List<ShaderData> shaders) {}

	/**
	 * Writes the full GPU state: {@code DrawState.write, int boundSlot, int texCount, texCount x (int id, int w,
	 * int h, Serialize int[] argb), int shaderCount, shaderCount x (int id, string source, Serialize float[]
	 * values, Serialize int[] samplers)}. Textures include slot 0 (the monitor) as raw ARGB, like 1.7.10.
	 */
	public static void writeGpuState(ByteArrayDataOutput out, GPU gpu) {
		synchronized (gpu) {
			gpu.state.write(out);
			out.writeInt(gpu.bindedSlot);
			int count = 0;
			for (Texture t : gpu.textures) if (t != null) count++;
			out.writeInt(count);
			for (int i = 0; i < gpu.textures.length; i++) {
				Texture t = gpu.textures[i];
				if (t == null) continue;
				out.writeInt(i);
				out.writeInt(t.getWidth());
				out.writeInt(t.getHeight());
				Serialize.write(out, t.getPixels(0, 0, t.getWidth(), t.getHeight()));
			}
			int shaderCount = 0;
			for (int i = 1; i < gpu.shaders.length; i++) if (gpu.shaders[i] != null) shaderCount++;
			out.writeInt(shaderCount);
			for (int i = 1; i < gpu.shaders.length; i++) {
				ShaderInstance sh = gpu.shaders[i];
				if (sh == null) continue;
				out.writeInt(i);
				Serialize.writeString(out, sh.source);
				Serialize.write(out, sh.values);
				Serialize.write(out, sh.samplers);
			}
		}
	}

	/** Reads the part of a NET_GPUSYNC payload after the target, validating sizes and ids. */
	public static GpuSnapshot readGpuState(ByteArrayDataInput in) throws IOException {
		DrawState state = new DrawState();
		state.read(in);
		int bound = in.readInt();
		int count = Serialize.checkLength(in, in.readInt(), 12);
		if (count > GPU.MAX_TEXTURES) throw new IOException("too many textures: " + count);
		List<TextureData> textures = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int id = in.readInt();
			int w = in.readInt(), h = in.readInt();
			Object px = Serialize.read(in);
			if (id < 0 || id >= GPU.MAX_TEXTURES) throw new IOException("bad texture id " + id);
			if (w <= 0 || h <= 0 || w > Texture.MAX_DIMENSION || h > Texture.MAX_DIMENSION) throw new IOException("bad texture size " + w + "x" + h);
			if (!(px instanceof int[]) || ((int[]) px).length != w * h) throw new IOException("bad pixels for texture " + id);
			textures.add(new TextureData(id, w, h, (int[]) px));
		}
		int shaderCount = Serialize.checkLength(in, in.readInt(), 4);
		if (shaderCount > GPU.MAX_SHADERS) throw new IOException("too many shaders: " + shaderCount);
		List<ShaderData> shaders = new ArrayList<>(shaderCount);
		for (int i = 0; i < shaderCount; i++) {
			int id = in.readInt();
			String src = Serialize.readString(in);
			Object values = Serialize.read(in);
			Object samplers = Serialize.read(in);
			if (id <= 0 || id >= GPU.MAX_SHADERS) throw new IOException("bad shader id " + id);
			if (!(values instanceof float[]) || !(samplers instanceof int[])) throw new IOException("bad uniforms for shader " + id);
			shaders.add(new ShaderData(id, src, (float[]) values, (int[]) samplers));
		}
		return new GpuSnapshot(state, bound, textures, shaders);
	}

	/**
	 * Replaces a (client) GPU's state with a snapshot, under {@code synchronized (gpu)}. Slot 0 is only
	 * written into the attached monitor texture when the sizes match; it is never replaced.
	 */
	public static void applyGpuState(GPU gpu, GpuSnapshot snap) {
		synchronized (gpu) {
			gpu.reset();
			DrawState s = gpu.state;
			DrawState src = snap.state();
			s.color = src.color;
			s.transform = new AffineTransform(src.transform);
			s.stack.clear();
			for (AffineTransform t : src.stack) s.stack.addLast(new AffineTransform(t));
			s.lineWidth = src.lineWidth;
			s.blend = src.blend;
			s.antialias = src.antialias;
			s.clip = src.clip == null ? null : new Rectangle(src.clip);

			for (TextureData td : snap.textures()) {
				if (td.id() == 0) {
					Texture screen = gpu.textures[0];
					if (screen != null && screen.getWidth() == td.width() && screen.getHeight() == td.height()) {
						screen.setPixels(0, 0, td.width(), td.height(), td.argb());
						screen.texUpdate();
					}
					continue;
				}
				Texture t = new Texture(td.width(), td.height());
				t.setPixels(0, 0, td.width(), td.height(), td.argb());
				gpu.textures[td.id()] = t;
			}
			int bound = snap.boundSlot();
			if (bound > 0 && bound < GPU.MAX_TEXTURES && gpu.textures[bound] != null) {
				gpu.bindedTexture = gpu.textures[bound];
				gpu.bindedSlot = bound;
			}
			for (ShaderData sd : snap.shaders()) {
				try {
					int slot = gpu.createShader(sd.source(), sd.id());
					ShaderInstance sh = gpu.shaders[slot];
					if (sd.values().length == sh.values.length) System.arraycopy(sd.values(), 0, sh.values, 0, sh.values.length);
					System.arraycopy(sd.samplers(), 0, sh.samplers, 0, Math.min(sd.samplers().length, sh.samplers.length));
				} catch (Exception e) {
					CCLights2.LOGGER.warn("Could not restore shader " + sd.id() + " from sync: " + e.getMessage());
				}
			}
			gpu.updateMonitors();
		}
	}

	// ------------------------------------------------------------------ client events

	/**
	 * The events a client may raise through NET_GPUEVENT, with their exact argument shapes:
	 * {@code monitor_scroll(int x, int y, int direction)}, {@code key(int keyCode, boolean repeat)},
	 * {@code key_up(int keyCode)}, {@code char(String of 1 or 2 chars)}. Anything else is rejected, so a
	 * modified client cannot queue arbitrary ComputerCraft events (such as {@code terminate}).
	 */
	public static boolean isAllowedClientEvent(String event, Object[] args) {
		if (event == null || args == null) return false;
		switch (event) {
		case "monitor_scroll":
			return args.length == 3 && args[0] instanceof Integer && args[1] instanceof Integer && args[2] instanceof Integer;
		case "key":
			return args.length == 2 && args[0] instanceof Integer && args[1] instanceof Boolean;
		case "key_up":
			return args.length == 1 && args[0] instanceof Integer;
		case "char":
			return args.length == 1 && args[0] instanceof String && !((String) args[0]).isEmpty() && ((String) args[0]).length() <= 2;
		default:
			return false;
		}
	}

	// ------------------------------------------------------------------ screenshots

	/** The byte table Lua receives with {@code tablet_image}: keys 1..n, values 0..255, all as Double. */
	public static Map<Double, Double> byteTable(byte[] data) {
		Map<Double, Double> table = new HashMap<>(data.length * 4 / 3 + 1);
		for (int i = 0; i < data.length; i++) table.put((double) (i + 1), (double) (data[i] & 0xFF));
		return table;
	}

	/** Reads a length-prefixed byte array of at most {@code max} bytes. */
	public static byte[] readBytes(ByteArrayDataInput in, int max) throws IOException {
		int len = Serialize.checkLength(in, in.readInt(), 1);
		if (len > max) throw new IOException(len + " bytes exceeds the limit of " + max);
		byte[] b = new byte[len];
		in.readFully(b);
		return b;
	}
}
