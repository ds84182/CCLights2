package ds.mods.CCLights2.tests;

import org.junit.jupiter.api.Test;

import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.shader.Program;
import ds.mods.CCLights2.gpu.shader.ShaderException;

/** Structs, arrays of structs, overloading. */
class StructTest {
	private final TestSupport.Checks checks = new TestSupport.Checks();

	private void check(boolean ok, String what) {
		checks.check(ok, what);
	}

	static int px(String src) throws Exception {
		ShaderInstance inst = new ShaderInstance(src);
		inst.prepare(1, 1, new int[4], new int[4]);
		int[] out = new int[1];
		inst.program.render(inst.values, null, null, null, 1, 1, out, 20000);
		return out[0] & 0xFFFFFF;
	}

	@Test
	void structsAndOverloads() throws Exception {
		int p = px("struct Hit { float d; vec3 col; int mat; };\n"
				+ "Hit trace() { Hit h; h.d = 0.5; h.col = vec3(0.0, 1.0, 0.0); h.mat = 2; return h; }\n"
				+ "void mainImage(out vec4 c, in vec2 f) { Hit h = trace(); h.col.b = float(h.mat) * 0.5; c = vec4(h.col, 1.0); }");
		check(p == 0x00FFFF, "struct fields, return by value, nested swizzle write (got " + Integer.toHexString(p) + ")");

		p = px("struct Ray { vec3 o; vec3 d; };\n"
				+ "struct Hit { float t; Ray r; };\n"
				+ "void mainImage(out vec4 c, in vec2 f) { Hit h = Hit(2.0, Ray(vec3(0.0), vec3(0.0, 0.5, 1.0))); Hit g = h; g.r.d.x = 1.0; c = vec4(g.r.d.x, h.r.d.x, h.r.d.y, 1.0); }");
		check(p == 0xFF0080, "constructors, nested structs, copy semantics (got " + Integer.toHexString(p) + ")");

		p = px("struct P { vec2 pos; float w; };\n"
				+ "void mainImage(out vec4 c, in vec2 f) { P ps[3]; for (int i = 0; i < 3; i++) { ps[i].pos = vec2(float(i) * 0.25, 0.0); ps[i].w = 0.5; } int k = 2; ps[k].w = 1.0; c = vec4(ps[2].pos.x, ps[k].w, ps[1].w, 1.0); }");
		check(p == 0x80FF80, "arrays of structs with dynamic index field writes (got " + Integer.toHexString(p) + ")");

		p = px("float hash(float n) { return 0.25; }\n"
				+ "float hash(vec2 p) { return 0.5; }\n"
				+ "vec3 hash(vec3 p) { return vec3(1.0); }\n"
				+ "void mainImage(out vec4 c, in vec2 f) { c = vec4(hash(1.0), hash(vec2(1.0)), hash(vec3(2.0)).x, 1.0); }");
		check(p == 0x4080FF, "overload by parameter type (got " + Integer.toHexString(p) + ")");

		p = px("float pick(float a) { return 0.0; }\nfloat pick(int a) { return 1.0; }\n"
				+ "void mainImage(out vec4 c, in vec2 f) { int i = 1; c = vec4(pick(i), pick(1.0), pick(2), 1.0); }");
		check(p == 0xFF00FF, "overload prefers exact int/float match (got " + Integer.toHexString(p) + ")");

		p = px("struct Light { vec3 pos; float power; };\n"
				+ "void apply(inout Light l, float k) { l.power *= k; }\n"
				+ "float use(Light l) { return l.power; }\n"
				+ "void mainImage(out vec4 c, in vec2 f) { Light l = Light(vec3(0.0), 0.5); apply(l, 2.0); c = vec4(use(l), 0.0, 0.0, 1.0); }");
		check(p == 0xFF0000, "struct inout parameter (got " + Integer.toHexString(p) + ")");

		try {
			Program.compile("struct A { float x; }; void mainImage(out vec4 c, in vec2 f) { A a; a.y = 1.0; c = vec4(1.0); }");
			check(false, "unknown field must fail");
		} catch (ShaderException e) {
			check(e.getMessage().contains("no field"), "unknown field rejected: " + e.getMessage());
		}
		try {
			Program.compile("float f(float x) { return x; } float f(float y) { return y; } void mainImage(out vec4 c, in vec2 q) { c = vec4(f(1.0)); }");
			check(false, "duplicate signature must fail");
		} catch (ShaderException e) {
			check(true, "duplicate signature rejected: " + e.getMessage());
		}
		try {
			Program.compile("float f(vec2 x) { return 1.0; } void mainImage(out vec4 c, in vec2 q) { c = vec4(f(vec3(1.0))); }");
			check(false, "no matching overload must fail");
		} catch (ShaderException e) {
			check(e.getMessage().contains("no matching overload"), "mismatch rejected: " + e.getMessage());
		}
		checks.assertAllPassed();
	}
}
