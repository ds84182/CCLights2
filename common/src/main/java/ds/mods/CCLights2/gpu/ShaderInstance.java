package ds.mods.CCLights2.gpu;

import java.util.Arrays;

import ds.mods.CCLights2.gpu.shader.GType;
import ds.mods.CCLights2.gpu.shader.Program;
import ds.mods.CCLights2.gpu.shader.Program.Uniform;
import ds.mods.CCLights2.gpu.shader.ShaderException;

/**
 * A compiled shader owned by a GPU together with its uniform values and sampler bindings.
 * The Shadertoy-style uniforms (iResolution, iTime, iFrame, iTimeDelta, iChannelResolution)
 * are filled in automatically unless a program set them explicitly.
 */
public final class ShaderInstance {
	public final Program program;
	public final String source;
	/** Numeric uniform values, flattened in declaration order. */
	public final float[] values;
	/** Texture id bound to each sampler slot, -1 for none. */
	public final int[] samplers;
	private final boolean[] userSet;
	private final long created = System.currentTimeMillis();
	private long lastRun = -1;
	private int frame = 0;

	public ShaderInstance(String source) throws ShaderException {
		this.source = source;
		this.program = Program.compile(source);
		this.values = new float[program.uniformFloats];
		this.samplers = new int[Math.max(1, program.samplerCount)];
		Arrays.fill(samplers, -1);
		this.userSet = new boolean[program.uniforms.size()];
	}

	/** Sets a numeric uniform from Lua; extra values are ignored, missing ones keep their old value. */
	public void setUniform(String name, double[] v) throws ShaderException {
		Uniform u = program.uniform(name);
		if (u == null) throw new ShaderException("no uniform named '" + name + "'");
		if (u.isSampler()) throw new ShaderException("uniform '" + name + "' is a sampler2D; pass a texture id");
		int off = program.uniformOffset(name);
		int n = u.type.size();
		for (int i = 0; i < n && i < v.length; i++) values[off + i] = (float) v[i];
		userSet[program.uniforms.indexOf(u)] = true;
	}

	public void setSampler(String name, int textureId) throws ShaderException {
		Uniform u = program.uniform(name);
		if (u == null) throw new ShaderException("no uniform named '" + name + "'");
		if (!u.isSampler()) throw new ShaderException("uniform '" + name + "' is not a sampler2D");
		samplers[u.slot] = textureId;
		userSet[program.uniforms.indexOf(u)] = true;
	}

	/** Fills the automatic uniforms for a run of size w x h (server side, before replicating). */
	public void prepare(int w, int h, int[] samplerW, int[] samplerH) {
		long now = System.currentTimeMillis();
		float time = (now - created) / 1000f;
		float delta = lastRun < 0 ? 1f / 20f : (now - lastRun) / 1000f;
		lastRun = now;
		for (int i = 0; i < program.uniforms.size(); i++) {
			Uniform u = program.uniforms.get(i);
			if (u.isSampler()) continue;
			int off = program.uniformOffset(u.name);
			if (u.name.equals("iResolution")) {
				values[off] = w;
				values[off + 1] = h;
				values[off + 2] = 1f;
			} else if (u.name.equals("iChannelResolution")) {
				for (int s = 0; s < 4 && off + s * 3 + 2 < values.length; s++) {
					values[off + s * 3] = s < samplerW.length ? samplerW[s] : 0;
					values[off + s * 3 + 1] = s < samplerH.length ? samplerH[s] : 0;
					values[off + s * 3 + 2] = 1f;
				}
			} else if (userSet[i]) {
				continue;
			} else if (u.name.equals("iTime") || u.name.equals("iGlobalTime")) {
				values[off] = time;
			} else if (u.name.equals("iTimeDelta")) {
				values[off] = delta;
			} else if (u.name.equals("iFrame")) {
				values[off] = frame;
			} else if (u.name.equals("iFrameRate")) {
				values[off] = delta > 0 ? 1f / delta : 20f;
			} else if (u.name.equals("iSampleRate")) {
				values[off] = 44100f;
			}
		}
		frame++;
	}

	/** Human readable uniform list for getShaderUniforms(). */
	public String describeUniform(Uniform u) {
		return u.type.toString();
	}

	public static boolean isValidType(GType t) {
		return t != null;
	}
}
