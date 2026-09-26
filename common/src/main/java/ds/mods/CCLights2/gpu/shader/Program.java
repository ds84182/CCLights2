package ds.mods.CCLights2.gpu.shader;

import java.util.List;

import ds.mods.CCLights2.gpu.shader.Ops.Ctx;
import ds.mods.CCLights2.gpu.shader.Ops.Op;

/**
 * A compiled fragment shader: instruction list plus the layout of its register file.
 */
public final strictfp class Program {
	public static final class Uniform {
		public final String name;
		public final GType type;
		/** Register of the value, or -1 for samplers. */
		public final int reg;
		/** Sampler slot, or -1 for numeric uniforms. */
		public final int slot;

		Uniform(String name, GType type, int reg, int slot) {
			this.name = name;
			this.type = type;
			this.reg = reg;
			this.slot = slot;
		}

		public boolean isSampler() {
			return slot >= 0;
		}
	}

	final Op[] ops;
	final int regCount;
	/** Initial register contents (constants). */
	final float[] init;
	public final List<Uniform> uniforms;
	public final int samplerCount;
	final int fragCoordReg;
	final int outReg;
	/** Total floats of numeric uniform storage, in declaration order. */
	public final int uniformFloats;

	Program(Op[] ops, int regCount, float[] init, List<Uniform> uniforms, int samplerCount, int fragCoordReg, int outReg) {
		this.ops = ops;
		this.regCount = regCount;
		this.init = init;
		this.uniforms = uniforms;
		this.samplerCount = samplerCount;
		this.fragCoordReg = fragCoordReg;
		this.outReg = outReg;
		int n = 0;
		for (Uniform u : uniforms) if (!u.isSampler()) n += u.type.size();
		this.uniformFloats = n;
	}

	public static Program compile(String source) throws ShaderException {
		return new Compiler().compile(Parser.parse(source));
	}

	public int instructionCount() {
		return ops.length;
	}

	public Uniform uniform(String name) {
		for (Uniform u : uniforms) if (u.name.equals(name)) return u;
		return null;
	}

	/** Offset of a numeric uniform inside the flattened value array. */
	public int uniformOffset(String name) {
		int off = 0;
		for (Uniform u : uniforms) {
			if (u.isSampler()) continue;
			if (u.name.equals(name)) return off;
			off += u.type.size();
		}
		return -1;
	}

	/**
	 * Renders a w x h region into out (ARGB, row major, top row first).
	 * @param values flattened numeric uniform values in declaration order
	 * @param maxOps instruction budget per pixel; exceeding it aborts the run
	 */
	public void render(float[] values, int[][] samplerPixels, int[] samplerW, int[] samplerH, int w, int h, int[] out, int maxOps) throws ShaderException {
		float[] r = new float[regCount];
		System.arraycopy(init, 0, r, 0, init.length);
		int off = 0;
		for (Uniform u : uniforms) {
			if (u.isSampler()) continue;
			int n = u.type.size();
			if (values != null && off + n <= values.length) System.arraycopy(values, off, r, u.reg, n);
			off += n;
		}
		Ctx ctx = new Ctx();
		ctx.samplerPixels = samplerPixels;
		ctx.samplerW = samplerW;
		ctx.samplerH = samplerH;
		Op[] code = ops;
		int len = code.length;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				r[fragCoordReg] = x + 0.5f;
				r[fragCoordReg + 1] = (h - 1 - y) + 0.5f;
				r[fragCoordReg + 2] = 0f;
				r[fragCoordReg + 3] = 1f;
				ctx.discard = false;
				int pc = 0;
				int budget = maxOps;
				while (pc < len) {
					pc = code[pc].exec(r, ctx, pc);
					if (--budget == 0) throw new ShaderException("shader exceeded " + maxOps + " instructions for one pixel (infinite loop?)");
				}
				if (ctx.discard) {
					out[y * w + x] = 0;
					continue;
				}
				out[y * w + x] = pack(r[outReg], r[outReg + 1], r[outReg + 2], r[outReg + 3]);
			}
		}
	}

	private static int pack(float rr, float g, float b, float a) {
		return (channel(a) << 24) | (channel(rr) << 16) | (channel(g) << 8) | channel(b);
	}

	private static int channel(float v) {
		if (!(v > 0f)) return 0; // also catches NaN
		if (v >= 1f) return 255;
		return (int) (v * 255f + 0.5f);
	}
}
