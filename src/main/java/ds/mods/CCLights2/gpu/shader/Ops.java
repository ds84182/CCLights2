package ds.mods.CCLights2.gpu.shader;

/**
 * Register-machine instructions the compiler emits. Every value lives in a float[] register file;
 * an op reads its operands from there and writes its result back. exec returns the next pc.
 * All maths goes through StrictMath so server and clients compute identical pixels.
 */
public final strictfp class Ops {
	private Ops() {}

	/** Per-run state shared by the ops. */
	public static final class Ctx {
		public int[][] samplerPixels;
		public int[] samplerW, samplerH;
		public boolean discard;
	}

	public abstract static class Op {
		public abstract int exec(float[] r, Ctx c, int pc);
	}

	public enum Bin { ADD, SUB, MUL, DIV, IDIV, MOD, POW, MIN, MAX, STEP, ATAN2, LT, LE, GT, GE, EQ, NE, AND, OR, XOR }

	public enum Un { NEG, NOT, SIN, COS, TAN, ASIN, ACOS, ATAN, SINH, COSH, TANH, EXP, LOG, EXP2, LOG2, SQRT, INVSQRT, ABS, SIGN, FLOOR, CEIL, ROUND, TRUNC, FRACT, RADIANS, DEGREES, TOBOOL }

	public enum Tern { CLAMP, MIX, SMOOTHSTEP }

	// ------------------------------------------------------------------ data movement

	public static final class Mov extends Op {
		final int dst, src, n;

		public Mov(int dst, int src, int n) {
			this.dst = dst;
			this.src = src;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			System.arraycopy(r, src, r, dst, n);
			return pc + 1;
		}
	}

	/** dst[i] = src[comps[i]] */
	public static final class Swizzle extends Op {
		final int dst, src;
		final int[] comps;

		public Swizzle(int dst, int src, int[] comps) {
			this.dst = dst;
			this.src = src;
			this.comps = comps;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			if (comps.length == 1) {
				r[dst] = r[src + comps[0]];
			} else {
				float a = r[src + comps[0]], b = r[src + comps[1]];
				float cc = comps.length > 2 ? r[src + comps[2]] : 0, d = comps.length > 3 ? r[src + comps[3]] : 0;
				r[dst] = a;
				r[dst + 1] = b;
				if (comps.length > 2) r[dst + 2] = cc;
				if (comps.length > 3) r[dst + 3] = d;
			}
			return pc + 1;
		}
	}

	/** dst[comps[i]] = src[i] */
	public static final class Scatter extends Op {
		final int dst, src;
		final int[] comps;

		public Scatter(int dst, int[] comps, int src) {
			this.dst = dst;
			this.comps = comps;
			this.src = src;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			for (int i = 0; i < comps.length; i++) r[dst + comps[i]] = r[src + i];
			return pc + 1;
		}
	}

	public static final class Const extends Op {
		final int dst;
		final float[] v;

		public Const(int dst, float[] v) {
			this.dst = dst;
			this.v = v;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			System.arraycopy(v, 0, r, dst, v.length);
			return pc + 1;
		}
	}

	/** dst[0..size) = r[base + clamp(idx)*elem + off ...] */
	public static final class LoadIdx extends Op {
		final int dst, base, idx, elem, count, off, size;

		public LoadIdx(int dst, int base, int idx, int elem, int count, int off, int size) {
			this.dst = dst;
			this.base = base;
			this.idx = idx;
			this.elem = elem;
			this.count = count;
			this.off = off;
			this.size = size;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			int i = (int) r[idx];
			if (i < 0) i = 0;
			if (i >= count) i = count - 1;
			System.arraycopy(r, base + i * elem + off, r, dst, size);
			return pc + 1;
		}
	}

	public static final class StoreIdx extends Op {
		final int base, idx, src, elem, count, off, size;

		public StoreIdx(int base, int idx, int src, int elem, int count, int off, int size) {
			this.base = base;
			this.idx = idx;
			this.src = src;
			this.elem = elem;
			this.count = count;
			this.off = off;
			this.size = size;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			int i = (int) r[idx];
			if (i < 0) i = 0;
			if (i >= count) i = count - 1;
			System.arraycopy(r, src, r, base + i * elem + off, size);
			return pc + 1;
		}
	}

	// ------------------------------------------------------------------ arithmetic

	public static final class Binary extends Op {
		final Bin kind;
		final int dst, a, b, n, as, bs;

		/** as/bs are strides: 1 for per-component operands, 0 for broadcast scalars. */
		public Binary(Bin kind, int dst, int a, int b, int n, int as, int bs) {
			this.kind = kind;
			this.dst = dst;
			this.a = a;
			this.b = b;
			this.n = n;
			this.as = as;
			this.bs = bs;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			for (int i = 0; i < n; i++) {
				float x = r[a + i * as], y = r[b + i * bs];
				float v;
				switch (kind) {
				case ADD: v = x + y; break;
				case SUB: v = x - y; break;
				case MUL: v = x * y; break;
				case DIV: v = x / y; break;
				case IDIV: v = y == 0 ? 0 : (float) ((int) x / (int) y); break;
				case MOD: v = x - y * (float) StrictMath.floor(x / y); break;
				case POW: v = (float) StrictMath.pow(x, y); break;
				case MIN: v = x < y ? x : y; break;
				case MAX: v = x > y ? x : y; break;
				case STEP: v = y < x ? 0f : 1f; break;
				case ATAN2: v = (float) StrictMath.atan2(x, y); break;
				case LT: v = x < y ? 1f : 0f; break;
				case LE: v = x <= y ? 1f : 0f; break;
				case GT: v = x > y ? 1f : 0f; break;
				case GE: v = x >= y ? 1f : 0f; break;
				case EQ: v = x == y ? 1f : 0f; break;
				case NE: v = x != y ? 1f : 0f; break;
				case AND: v = (x != 0f && y != 0f) ? 1f : 0f; break;
				case OR: v = (x != 0f || y != 0f) ? 1f : 0f; break;
				case XOR: v = ((x != 0f) != (y != 0f)) ? 1f : 0f; break;
				default: v = 0; break;
				}
				r[dst + i] = v;
			}
			return pc + 1;
		}
	}

	public static final class Unary extends Op {
		final Un kind;
		final int dst, a, n;

		public Unary(Un kind, int dst, int a, int n) {
			this.kind = kind;
			this.dst = dst;
			this.a = a;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			for (int i = 0; i < n; i++) {
				float x = r[a + i];
				float v;
				switch (kind) {
				case NEG: v = -x; break;
				case NOT: v = x == 0f ? 1f : 0f; break;
				case SIN: v = (float) StrictMath.sin(x); break;
				case COS: v = (float) StrictMath.cos(x); break;
				case TAN: v = (float) StrictMath.tan(x); break;
				case ASIN: v = (float) StrictMath.asin(x); break;
				case ACOS: v = (float) StrictMath.acos(x); break;
				case ATAN: v = (float) StrictMath.atan(x); break;
				case SINH: v = (float) StrictMath.sinh(x); break;
				case COSH: v = (float) StrictMath.cosh(x); break;
				case TANH: v = (float) StrictMath.tanh(x); break;
				case EXP: v = (float) StrictMath.exp(x); break;
				case LOG: v = (float) StrictMath.log(x); break;
				case EXP2: v = (float) StrictMath.pow(2.0, x); break;
				case LOG2: v = (float) (StrictMath.log(x) / 0.6931471805599453); break;
				case SQRT: v = (float) StrictMath.sqrt(x); break;
				case INVSQRT: v = (float) (1.0 / StrictMath.sqrt(x)); break;
				case ABS: v = x < 0 ? -x : x; break;
				case SIGN: v = x > 0 ? 1f : (x < 0 ? -1f : 0f); break;
				case FLOOR: v = (float) StrictMath.floor(x); break;
				case CEIL: v = (float) StrictMath.ceil(x); break;
				case ROUND: v = (float) StrictMath.floor(x + 0.5); break;
				case TRUNC: v = (float) (int) x; break;
				case FRACT: v = x - (float) StrictMath.floor(x); break;
				case RADIANS: v = (float) (x * (StrictMath.PI / 180.0)); break;
				case DEGREES: v = (float) (x * (180.0 / StrictMath.PI)); break;
				case TOBOOL: v = x != 0f ? 1f : 0f; break;
				default: v = 0; break;
				}
				r[dst + i] = v;
			}
			return pc + 1;
		}
	}

	public static final class Ternary extends Op {
		final Tern kind;
		final int dst, a, b, cc, n, as, bs, cs;

		public Ternary(Tern kind, int dst, int a, int b, int cc, int n, int as, int bs, int cs) {
			this.kind = kind;
			this.dst = dst;
			this.a = a;
			this.b = b;
			this.cc = cc;
			this.n = n;
			this.as = as;
			this.bs = bs;
			this.cs = cs;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			for (int i = 0; i < n; i++) {
				float x = r[a + i * as], y = r[b + i * bs], z = r[cc + i * cs];
				float v;
				switch (kind) {
				case CLAMP: v = x < y ? y : (x > z ? z : x); break;
				case MIX: v = x * (1f - z) + y * z; break;
				case SMOOTHSTEP: {
					float t = (z - x) / (y - x);
					t = t < 0 ? 0 : (t > 1 ? 1 : t);
					v = t * t * (3f - 2f * t);
					break;
				}
				default: v = 0; break;
				}
				r[dst + i] = v;
			}
			return pc + 1;
		}
	}

	public static final class Dot extends Op {
		final int dst, a, b, n;

		public Dot(int dst, int a, int b, int n) {
			this.dst = dst;
			this.a = a;
			this.b = b;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float s = 0;
			for (int i = 0; i < n; i++) s += r[a + i] * r[b + i];
			r[dst] = s;
			return pc + 1;
		}
	}

	public static final class Length extends Op {
		final int dst, a, n;

		public Length(int dst, int a, int n) {
			this.dst = dst;
			this.a = a;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float s = 0;
			for (int i = 0; i < n; i++) s += r[a + i] * r[a + i];
			r[dst] = (float) StrictMath.sqrt(s);
			return pc + 1;
		}
	}

	public static final class Distance extends Op {
		final int dst, a, b, n;

		public Distance(int dst, int a, int b, int n) {
			this.dst = dst;
			this.a = a;
			this.b = b;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float s = 0;
			for (int i = 0; i < n; i++) {
				float d = r[a + i] - r[b + i];
				s += d * d;
			}
			r[dst] = (float) StrictMath.sqrt(s);
			return pc + 1;
		}
	}

	public static final class Normalize extends Op {
		final int dst, a, n;

		public Normalize(int dst, int a, int n) {
			this.dst = dst;
			this.a = a;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float s = 0;
			for (int i = 0; i < n; i++) s += r[a + i] * r[a + i];
			float inv = s > 0 ? (float) (1.0 / StrictMath.sqrt(s)) : 0f;
			for (int i = 0; i < n; i++) r[dst + i] = r[a + i] * inv;
			return pc + 1;
		}
	}

	public static final class Cross extends Op {
		final int dst, a, b;

		public Cross(int dst, int a, int b) {
			this.dst = dst;
			this.a = a;
			this.b = b;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float ax = r[a], ay = r[a + 1], az = r[a + 2];
			float bx = r[b], by = r[b + 1], bz = r[b + 2];
			r[dst] = ay * bz - az * by;
			r[dst + 1] = az * bx - ax * bz;
			r[dst + 2] = ax * by - ay * bx;
			return pc + 1;
		}
	}

	/** reflect(I, N) = I - 2 dot(N, I) N */
	public static final class Reflect extends Op {
		final int dst, i, nrm, n;

		public Reflect(int dst, int i, int nrm, int n) {
			this.dst = dst;
			this.i = i;
			this.nrm = nrm;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float d = 0;
			for (int k = 0; k < n; k++) d += r[nrm + k] * r[i + k];
			for (int k = 0; k < n; k++) r[dst + k] = r[i + k] - 2f * d * r[nrm + k];
			return pc + 1;
		}
	}

	public static final class Refract extends Op {
		final int dst, i, nrm, eta, n;

		public Refract(int dst, int i, int nrm, int eta, int n) {
			this.dst = dst;
			this.i = i;
			this.nrm = nrm;
			this.eta = eta;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float e = r[eta];
			float d = 0;
			for (int k = 0; k < n; k++) d += r[nrm + k] * r[i + k];
			float k2 = 1f - e * e * (1f - d * d);
			if (k2 < 0) {
				for (int k = 0; k < n; k++) r[dst + k] = 0;
			} else {
				float s = e * d + (float) StrictMath.sqrt(k2);
				for (int k = 0; k < n; k++) r[dst + k] = e * r[i + k] - s * r[nrm + k];
			}
			return pc + 1;
		}
	}

	/** Column-major matrix product: A (m x k) * B (k x n) -> dst (m x n). */
	public static final class MatMul extends Op {
		final int dst, a, b, m, k, n;

		public MatMul(int dst, int a, int b, int m, int k, int n) {
			this.dst = dst;
			this.a = a;
			this.b = b;
			this.m = m;
			this.k = k;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float[] tmp = new float[m * n];
			for (int col = 0; col < n; col++) {
				for (int row = 0; row < m; row++) {
					float s = 0;
					for (int t = 0; t < k; t++) s += r[a + t * m + row] * r[b + col * k + t];
					tmp[col * m + row] = s;
				}
			}
			System.arraycopy(tmp, 0, r, dst, tmp.length);
			return pc + 1;
		}
	}

	public static final class Transpose extends Op {
		final int dst, a, n;

		public Transpose(int dst, int a, int n) {
			this.dst = dst;
			this.a = a;
			this.n = n;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float[] tmp = new float[n * n];
			for (int col = 0; col < n; col++) for (int row = 0; row < n; row++) tmp[row * n + col] = r[a + col * n + row];
			System.arraycopy(tmp, 0, r, dst, tmp.length);
			return pc + 1;
		}
	}

	/** dst = min or max over n components (all()/any() on bvecs, == on vectors). */
	public static final class Reduce extends Op {
		final int dst, a, n;
		final boolean all;

		public Reduce(int dst, int a, int n, boolean all) {
			this.dst = dst;
			this.a = a;
			this.n = n;
			this.all = all;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			float v = r[a];
			for (int i = 1; i < n; i++) v = all ? StrictMath.min(v, r[a + i]) : StrictMath.max(v, r[a + i]);
			r[dst] = v;
			return pc + 1;
		}
	}

	// ------------------------------------------------------------------ control flow

	public static final class Jmp extends Op {
		public int target;

		public Jmp(int target) {
			this.target = target;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			return target;
		}
	}

	/** Jump when the condition register is zero (false). */
	public static final class Jz extends Op {
		final int cond;
		public int target;

		public Jz(int cond, int target) {
			this.cond = cond;
			this.target = target;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			return r[cond] == 0f ? target : pc + 1;
		}
	}

	public static final class Discard extends Op {
		@Override
		public int exec(float[] r, Ctx c, int pc) {
			c.discard = true;
			return Integer.MAX_VALUE;
		}
	}

	// ------------------------------------------------------------------ textures

	/** Bilinear sample of a sampler slot at uv (repeat wrap, GL convention: v=0 is the bottom row). */
	public static final class Tex extends Op {
		final int dst, slot, uv;

		public Tex(int dst, int slot, int uv) {
			this.dst = dst;
			this.slot = slot;
			this.uv = uv;
		}

		@Override
		public int exec(float[] r, Ctx c, int pc) {
			int[] px = c.samplerPixels != null && slot < c.samplerPixels.length ? c.samplerPixels[slot] : null;
			if (px == null) {
				r[dst] = r[dst + 1] = r[dst + 2] = 0f;
				r[dst + 3] = 1f;
				return pc + 1;
			}
			int w = c.samplerW[slot], h = c.samplerH[slot];
			float u = r[uv], v = r[uv + 1];
			float fx = (u - (float) StrictMath.floor(u)) * w - 0.5f;
			float fy = (1f - (v - (float) StrictMath.floor(v))) * h - 0.5f;
			int x0 = (int) StrictMath.floor(fx), y0 = (int) StrictMath.floor(fy);
			float tx = fx - x0, ty = fy - y0;
			int x1 = x0 + 1, y1 = y0 + 1;
			x0 = wrap(x0, w); x1 = wrap(x1, w); y0 = wrap(y0, h); y1 = wrap(y1, h);
			int p00 = px[y0 * w + x0], p10 = px[y0 * w + x1], p01 = px[y1 * w + x0], p11 = px[y1 * w + x1];
			for (int ch = 0; ch < 4; ch++) {
				int shift = ch == 3 ? 24 : 16 - ch * 8;
				float c00 = ((p00 >>> shift) & 0xFF), c10 = ((p10 >>> shift) & 0xFF), c01 = ((p01 >>> shift) & 0xFF), c11 = ((p11 >>> shift) & 0xFF);
				float top = c00 + (c10 - c00) * tx, bottom = c01 + (c11 - c01) * tx;
				r[dst + ch] = (top + (bottom - top) * ty) / 255f;
			}
			return pc + 1;
		}

		private static int wrap(int i, int n) {
			i %= n;
			return i < 0 ? i + n : i;
		}
	}
}
