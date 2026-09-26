package ds.mods.CCLights2.tests;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.shader.Program;
import ds.mods.CCLights2.gpu.shader.ShaderException;

/** Compiles and renders every shipped shader, plus a few language-feature checks. */
class ShaderTest {
	private final TestSupport.Checks checks = new TestSupport.Checks();

	private void check(boolean ok, String what) {
		checks.check(ok, what);
	}

	static int[] render(String src, int w, int h, float time, int[][] samplers, int[] sw, int[] sh) throws Exception {
		ShaderInstance inst = new ShaderInstance(src);
		int off = inst.program.uniformOffset("iTime");
		if (off >= 0) inst.setUniform("iTime", new double[] { time });
		if (inst.program.uniformOffset("time") >= 0) inst.setUniform("time", new double[] { time });
		if (inst.program.uniformOffset("resolution") >= 0) inst.setUniform("resolution", new double[] { w, h });
		inst.prepare(w, h, sw == null ? new int[4] : sw, sh == null ? new int[4] : sh);
		int[] out = new int[w * h];
		inst.program.render(inst.values, samplers, sw, sh, w, h, out, 20000);
		return out;
	}

	static int pixel(int[] px, int w, int x, int y) {
		return px[y * w + x] & 0xFFFFFF;
	}

	@Test
	void languageAndShippedShaders() throws Exception {
		File shaderDir = new File(TestSupport.luaDir(), "shaders");
		File outDir = TestSupport.outDir("shaders");

		// --- language checks
		int[] p = render("void mainImage(out vec4 c, in vec2 f) { c = vec4(1.0, 0.5, 0.0, 1.0); }", 4, 4, 0, null, null, null);
		check(pixel(p, 4, 0, 0) == 0xFF8000, "constant colour");

		p = render("void mainImage(out vec4 c, in vec2 f) { vec2 uv = f / iResolution.xy; c = vec4(uv, 0.0, 1.0); }", 256, 128, 0, null, null, null);
		check((pixel(p, 256, 255, 0) >> 16) >= 250 && ((pixel(p, 256, 255, 0) >> 8) & 0xFF) >= 250, "fragCoord: top-right is (1,1) -> yellow (got " + Integer.toHexString(pixel(p, 256, 255, 0)) + ")");
		check(((pixel(p, 256, 0, 127) >> 8) & 0xFF) <= 2 && (pixel(p, 256, 0, 127) >> 16) <= 2, "fragCoord: bottom-left is (0,0)");

		p = render("float sq(float x) { return x * x; }\n"
				+ "vec3 col(int i) { if (i == 0) return vec3(1,0,0); else if (i == 1) return vec3(0,1,0); return vec3(0,0,1); }\n"
				+ "void mainImage(out vec4 c, in vec2 f) { int k = int(f.x); float s = 0.0; for (int i = 0; i < 4; i++) { s += sq(float(i)); } c = vec4(col(k) * step(13.5, s), 1.0); }",
				3, 1, 0, null, null, null);
		check(pixel(p, 3, 0, 0) == 0xFF0000 && pixel(p, 3, 1, 0) == 0x00FF00 && pixel(p, 3, 2, 0) == 0x0000FF, "functions, loops, if/else, int conversion (sum of squares 0..3 = 14)");

		p = render("void mainImage(out vec4 c, in vec2 f) { mat2 m = mat2(0.0, 1.0, -1.0, 0.0); vec2 v = m * vec2(1.0, 0.0); c = vec4(v.y, -v.x + 1.0, 0.0, 1.0); }", 1, 1, 0, null, null, null);
		check(pixel(p, 1, 0, 0) == 0xFFFF00, "mat2 * vec2 rotates (1,0) to (0,1) (got " + Integer.toHexString(pixel(p, 1, 0, 0)) + ")");

		p = render("void mainImage(out vec4 c, in vec2 f) { vec4 v = vec4(0.0); v.zx = vec2(1.0, 0.5); v.w = 1.0; c = v; }", 1, 1, 0, null, null, null);
		check(pixel(p, 1, 0, 0) == 0x8000FF, "swizzle write v.zx (got " + Integer.toHexString(pixel(p, 1, 0, 0)) + ")");

		p = render("#define N 3\nvoid mainImage(out vec4 c, in vec2 f) { float a[N]; a[0] = 0.2; a[1] = 0.4; a[2] = 0.6; int i = 2; float s = a[i] + a[0]; c = vec4(s, a[1], 0.0, 1.0); }", 1, 1, 0, null, null, null);
		check((pixel(p, 1, 0, 0) >> 16) == 204 && ((pixel(p, 1, 0, 0) >> 8) & 0xFF) == 102, "#define and arrays with dynamic index");

		p = render("void mainImage(out vec4 c, in vec2 f) { float x = 5.0; x -= 2.0; x *= 3.0; int i = 7 / 2; x += float(i); c = vec4(x / 12.0, 0.0, 0.0, 1.0); }", 1, 1, 0, null, null, null);
		check((pixel(p, 1, 0, 0) >> 16) == 255, "compound assignment and integer division (3*3+3 = 12)");

		p = render("void mainImage(out vec4 c, in vec2 f) { float t = f.x > 1.0 ? 1.0 : 0.0; c = vec4(t, 1.0 - t, 0.0, 1.0); }", 2, 1, 0, null, null, null);
		check(pixel(p, 2, 0, 0) == 0x00FF00 && pixel(p, 2, 1, 0) == 0xFF0000, "ternary");

		// texture sampling: 2x1 red|blue sampler
		int[][] samplers = { { 0xFFFF0000, 0xFF0000FF } };
		p = render("void mainImage(out vec4 c, in vec2 f) { c = texture2D(iChannel0, vec2(f.x / 2.0, 0.5)); }", 2, 1, 0, samplers, new int[] { 2 }, new int[] { 1 });
		check(pixel(p, 2, 0, 0) == 0xFF0000 && pixel(p, 2, 1, 0) == 0x0000FF, "texture2D samples iChannel0 (got " + Integer.toHexString(pixel(p, 2, 0, 0)) + "," + Integer.toHexString(pixel(p, 2, 1, 0)) + ")");

		try {
			render("void mainImage(out vec4 c, in vec2 f) { while (true) { c = vec4(1.0); } }", 1, 1, 0, null, null, null);
			check(false, "infinite loop must hit the instruction budget");
		} catch (ShaderException e) {
			check(true, "infinite loop rejected: " + e.getMessage());
		}
		try {
			Program.compile("void mainImage(out vec4 c, in vec2 f) { c = vec4(undefinedThing); }");
			check(false, "unknown identifier must fail");
		} catch (ShaderException e) {
			check(e.getMessage().contains("line 1"), "compile error carries the line: " + e.getMessage());
		}
		try {
			Program.compile("float f(float x) { return f(x); } void mainImage(out vec4 c, in vec2 f2) { c = vec4(f(1.0)); }");
			check(false, "recursion must fail");
		} catch (ShaderException e) {
			check(true, "recursion rejected: " + e.getMessage());
		}

		// --- shipped shaders
		File[] files = shaderDir.listFiles();
		java.util.Arrays.sort(files);
		for (File f : files) {
			if (!f.getName().endsWith(".frag")) continue;
			String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			long t0 = System.nanoTime();
			try {
				ShaderInstance inst = new ShaderInstance(src);
				int w = 256, h = 144;
				if (inst.program.uniformOffset("resolution") >= 0) inst.setUniform("resolution", new double[] { w, h });
				if (inst.program.uniformOffset("time") >= 0) inst.setUniform("time", new double[] { 3.0 });
				if (inst.program.uniformOffset("iTime") >= 0) inst.setUniform("iTime", new double[] { 3.0 });
				if (inst.program.uniformOffset("iMouse") >= 0) inst.setUniform("iMouse", new double[] { 0, 0, 0, 0 });
				inst.prepare(w, h, new int[4], new int[4]);
				int[] out = new int[w * h];
				long t1 = System.nanoTime();
				inst.program.render(inst.values, null, null, null, w, h, out, 20000);
				long t2 = System.nanoTime();
				BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				img.setRGB(0, 0, w, h, out, 0, w);
				ImageIO.write(img, "png", new File(outDir, f.getName().replace(".frag", ".png")));
				int nonBlack = 0;
				for (int v : out) if ((v & 0xFFFFFF) != 0) nonBlack++;
				check(nonBlack > out.length / 10, String.format("%s: %d instructions, compiled in %d ms, rendered 256x144 in %d ms, %d%% lit",
						f.getName(), inst.program.instructionCount(), (t1 - t0) / 1000000, (t2 - t1) / 1000000, nonBlack * 100 / out.length));
			} catch (ShaderException e) {
				check(false, f.getName() + ": " + e.getMessage());
			}
		}
		checks.assertAllPassed();
	}
}
