package ds.mods.CCLights2.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.luaj.vm2.LoadState;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import dan200.computercraft.api.lua.LuaException;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.GpuLuaApi;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;

/**
 * Runs the shipped Lua programs on a fake ComputerCraft computer wired to a real GPU and Monitor.
 * Runs gpudemo, shaderdemo, tutorial, tabletcam and tabletdemo at 192x128; frames go to build/test-output/lua.
 */
class LuaDemoTest {
	static File luaDir, romDir, outDir;
	static GPU gpu;
	static Monitor mon;
	static GpuLuaApi api;
	static LuaTable globals;
	static int frame = 0;
	static String program;
	static int resumes = 0;
	static int timerId = 0;
	static List<String> log = new ArrayList<String>();

	static final int W = 192, H = 128;

	@BeforeAll
	static void loadResources() throws Exception {
		luaDir = TestSupport.luaDir();
		romDir = TestSupport.resource("ccrom");
		outDir = TestSupport.outDir("lua");
		TestSupport.loadFont();
	}

	@ParameterizedTest(name = "{0} at 192x128")
	@ValueSource(strings = { "gpudemo", "shaderdemo", "tutorial", "tabletcam", "tabletdemo" })
	void runsToCompletion(String name) throws Exception {
		program = name;
		log.clear();
		timerId = 0;
		System.out.println("=== " + program + " on " + W + "x" + H);
		runProgram(program, W, H);
		System.out.println("ok:   " + program + " finished (" + resumes + " events, " + frame + " frames)");
	}

	static void snapshot(String tag) throws Exception {
		Texture t = mon.tex;
		BufferedImage img = new BufferedImage(t.getWidth(), t.getHeight(), BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, t.getWidth(), t.getHeight(), t.getPixels(0, 0, t.getWidth(), t.getHeight()), 0, t.getWidth());
		ImageIO.write(img, "png", new File(outDir, String.format("%s_%s_%03d_%s.png", program, mon.getWidth() + "x" + mon.getHeight(), frame++, tag)));
	}

	// ------------------------------------------------------------------ value conversion

	static Object toJava(LuaValue v) {
		if (v.isnil()) return null;
		if (v.isboolean()) return v.toboolean();
		if (v.isnumber()) return v.todouble();
		if (v.isstring()) return v.tojstring();
		if (v.istable()) {
			Map<Object, Object> m = new HashMap<Object, Object>();
			LuaTable t = v.checktable();
			LuaValue k = LuaValue.NIL;
			while (true) {
				Varargs n = t.next(k);
				k = n.arg1();
				if (k.isnil()) break;
				Object key = k.isnumber() ? (Object) k.todouble() : k.tojstring();
				m.put(key, toJava(n.arg(2)));
			}
			return m;
		}
		return v.tojstring();
	}

	static LuaValue toLua(Object o) {
		if (o == null) return LuaValue.NIL;
		if (o instanceof Boolean) return LuaValue.valueOf((Boolean) o);
		if (o instanceof Number) return LuaValue.valueOf(((Number) o).doubleValue());
		if (o instanceof String) return LuaValue.valueOf((String) o);
		if (o instanceof Map) {
			LuaTable t = new LuaTable();
			for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
				LuaValue k = e.getKey() instanceof Number ? LuaValue.valueOf(((Number) e.getKey()).doubleValue()) : LuaValue.valueOf(String.valueOf(e.getKey()));
				t.set(k, toLua(e.getValue()));
			}
			return t;
		}
		return LuaValue.NIL;
	}

	static LuaTable gpuTable() {
		LuaTable t = new LuaTable();
		for (int i = 0; i < GpuLuaApi.METHOD_NAMES.length; i++) {
			final int method = i;
			t.set(GpuLuaApi.METHOD_NAMES[i], new VarArgFunction() {
				@Override
				public Varargs invoke(Varargs a) {
					Object[] jargs = new Object[a.narg()];
					for (int k = 0; k < jargs.length; k++) jargs[k] = toJava(a.arg(k + 1));
					try {
						Object[] ret = api.call(method, jargs, null);
						if (ret == null) return LuaValue.NONE;
						LuaValue[] vals = new LuaValue[ret.length];
						for (int k = 0; k < ret.length; k++) vals[k] = toLua(ret[k]);
						return LuaValue.varargsOf(vals);
					} catch (LuaException e) {
						throw new LuaError(e.getMessage());
					}
				}
			});
		}
		return t;
	}

	// ------------------------------------------------------------------ fake computer

	static File resolve(String path) {
		String p = path.replace('\\', '/');
		if (p.startsWith("/")) p = p.substring(1);
		if (p.startsWith("cclights2/")) return new File(luaDir, p.substring("cclights2/".length()));
		return new File(luaDir, p);
	}

	static LuaValue loadFile(File f, LuaTable env) throws Exception {
		return LoadState.load(new FileInputStream(f), "=" + f.getName(), env);
	}

	static void setup(int w, int h) throws Exception {
		gpu = new GPU(8192);
		mon = new Monitor(w, h, null);
		mon.addGPU(gpu);
		api = new GpuLuaApi(gpu, new GpuLuaApi.Host() {
			@Override
			public void setFrame(boolean on) {}
		});
		globals = JsePlatform.standardGlobals();
		globals.get("table").set("unpack", globals.get("unpack"));
		final LuaTable gpuT = gpuTable();

		LuaTable peripheral = new LuaTable();
		peripheral.set("find", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				return a.arg1().tojstring().equals("GPU") ? gpuT : LuaValue.NIL;
			}
		});
		peripheral.set("wrap", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				return gpuT;
			}
		});
		globals.set("peripheral", peripheral);

		// fs
		LuaTable fs = new LuaTable();
		fs.set("combine", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				String base = a.arg1().tojstring(), rel = a.arg(2).tojstring();
				if (rel.equals("..")) {
					int i = base.lastIndexOf('/');
					return LuaValue.valueOf(i > 0 ? base.substring(0, i) : "");
				}
				return LuaValue.valueOf(base.isEmpty() ? rel : base + "/" + rel);
			}
		});
		fs.set("getName", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				String p = a.arg1().tojstring();
				return LuaValue.valueOf(p.substring(p.lastIndexOf('/') + 1));
			}
		});
		fs.set("list", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				LuaTable t = new LuaTable();
				File d = resolve(a.arg1().tojstring());
				String[] names = d.list();
				if (names != null) {
					java.util.Arrays.sort(names);
					for (String n : names) t.insert(t.length() + 1, LuaValue.valueOf(n));
				}
				return t;
			}
		});
		fs.set("open", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				final File f = resolve(a.arg1().tojstring());
				if (!f.isFile()) return LuaValue.NIL;
				try {
					final byte[] data = Files.readAllBytes(f.toPath());
					final int[] pos = { 0 };
					LuaTable h = new LuaTable();
					h.set("readAll", new VarArgFunction() {
						@Override
						public Varargs invoke(Varargs x) {
							return LuaValue.valueOf(new String(data, StandardCharsets.UTF_8));
						}
					});
					h.set("read", new VarArgFunction() {
						@Override
						public Varargs invoke(Varargs x) {
							if (pos[0] >= data.length) return LuaValue.NIL;
							return LuaValue.valueOf(data[pos[0]++] & 0xFF);
						}
					});
					h.set("close", new VarArgFunction() {
						@Override
						public Varargs invoke(Varargs x) {
							return LuaValue.NONE;
						}
					});
					return h;
				} catch (Exception e) {
					return LuaValue.NIL;
				}
			}
		});
		globals.set("fs", fs);

		LuaTable shell = new LuaTable();
		shell.set("getRunningProgram", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				return LuaValue.valueOf("cclights2/" + program);
			}
		});
		globals.set("shell", shell);

		globals.set("dofile", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				try {
					return loadFile(resolve(a.arg1().tojstring()), globals).invoke(LuaValue.NONE);
				} catch (LuaError e) {
					throw e;
				} catch (Exception e) {
					throw new LuaError(e.toString());
				}
			}
		});

		// os: pullEvent yields to the driver, timers are answered immediately
		LuaTable os = globals.get("os").checktable();
		os.set("clock", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				return LuaValue.valueOf(System.nanoTime() / 1e9);
			}
		});
		os.set("startTimer", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs a) {
				return LuaValue.valueOf(++timerId);
			}
		});
		os.set("pullEventRaw", globals.get("coroutine").get("yield"));
		// CC APIs from the real jar: keys and parallel
		for (String apiName : new String[] { "keys", "parallel" }) {
			LuaTable env = new LuaTable();
			env.setmetatable(LuaValue.tableOf(new LuaValue[] { LuaValue.valueOf("__index"), globals }));
			env.set("_ENV", env); // CC's API loader provides _ENV
			loadFile(new File(romDir, "rom/apis/" + apiName), env).invoke(LuaValue.NONE);
			LuaTable apiT = new LuaTable();
			LuaValue k = LuaValue.NIL;
			while (true) {
				Varargs n = env.next(k);
				k = n.arg1();
				if (k.isnil()) break;
				apiT.set(k, n.arg(2));
			}
			globals.set(apiName, apiT);
		}
		String prelude = ""
				+ "function os.pullEvent(f) local e = { coroutine.yield(f) } return unpack(e) end\n"
				+ "function sleep(n) local t = os.startTimer(n or 0) repeat local _, id = os.pullEvent('timer') until id == t end\n";
		LoadState.load(new ByteArrayInputStream(prelude.getBytes(StandardCharsets.UTF_8)), "=prelude", globals).invoke(LuaValue.NONE);
	}

	/** Drives the program coroutine, answering yields with scripted events. */
	static void runProgram(String name, int w, int h) throws Exception {
		setup(w, h);
		frame = 0;
		resumes = 0;
		final int maxClicks = 150;
		final int maxResumes = 60000;
		int clicks = 0;
		LuaValue chunk = loadFile(resolve(name), globals);
		LuaThread co = new LuaThread(chunk, globals);
		Varargs result = co.resume(LuaValue.NONE);
		int timersSinceSnapshot = 0;
		while (co.getStatus().equals("suspended")) {
			if (!result.arg1().toboolean()) throw new RuntimeException(result.arg(2).tojstring());
			String filter = result.arg(2).isnil() ? null : result.arg(2).tojstring();
			resumes++;
			if (resumes > maxResumes) throw new RuntimeException("program did not finish after " + maxResumes + " events");
			Varargs event;
			if ("timer".equals(filter)) {
				event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("timer"), LuaValue.valueOf(timerId) });
				if (++timersSinceSnapshot % 200 == 0) snapshot("t");
			} else if (filter == null && timerId > 0 && resumes % 40 != 0) {
				// programs that wait for any event usually have a timer running (shaderdemo, gpudemo)
				event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("timer"), LuaValue.valueOf(timerId) });
				if (++timersSinceSnapshot % 200 == 0) snapshot("t");
			} else if (program.equals("tabletdemo")) {
				// scripted tablet session: pick a colour, drag a stroke, type, backspace, send a photo, quit
				clicks++;
				LuaValue side = LuaValue.valueOf("left");
				double cell = Math.floor(w / 9.0);
				switch (clicks) {
				case 1: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_down"), LuaValue.valueOf(0), LuaValue.valueOf(cell * 0.5), LuaValue.valueOf(5), LuaValue.valueOf(1), side }); break;
				case 2: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_up"), LuaValue.valueOf(0), LuaValue.valueOf(cell * 0.5), LuaValue.valueOf(5), LuaValue.valueOf(1), side }); break;
				case 3: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_down"), LuaValue.valueOf(0), LuaValue.valueOf(w * 0.2), LuaValue.valueOf(h * 0.4), LuaValue.valueOf(2), side }); break;
				case 4: case 5: case 6: case 7: case 8: {
					double t = (clicks - 3) / 5.0;
					event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_move"), LuaValue.valueOf(0), LuaValue.valueOf(w * (0.2 + 0.6 * t)), LuaValue.valueOf(h * (0.4 + 0.3 * Math.sin(t * Math.PI))), LuaValue.valueOf(2), side });
					break;
				}
				case 9: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_up"), LuaValue.valueOf(0), LuaValue.valueOf(w * 0.8), LuaValue.valueOf(h * 0.4), LuaValue.valueOf(2), side }); break;
				case 10: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("char"), LuaValue.valueOf("H"), side }); break;
				case 11: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("char"), LuaValue.valueOf("i"), side }); break;
				case 12: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("char"), LuaValue.valueOf("!"), side }); break;
				case 13: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("key"), LuaValue.valueOf(14), LuaValue.valueOf(false), side }); break;
				case 14: {
					try {
						byte[] png = Files.readAllBytes(new File(luaDir, "tutorial_files/smile.png").toPath());
						LuaTable t = new LuaTable();
						for (int b = 0; b < png.length; b++) t.set(b + 1, LuaValue.valueOf(png[b] & 0xFF));
						event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("tablet_image"), t, LuaValue.valueOf("Tester"), side });
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					break;
				}
				case 15: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_down"), LuaValue.valueOf(0), LuaValue.valueOf(w * 0.5), LuaValue.valueOf(h * 0.7), LuaValue.valueOf(3), side }); break;
				case 16: event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_up"), LuaValue.valueOf(0), LuaValue.valueOf(w * 0.5), LuaValue.valueOf(h * 0.7), LuaValue.valueOf(3), side }); break;
				default:
					snapshot("done");
					event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("char"), LuaValue.valueOf("q"), side });
				}
				if (clicks <= 16) snapshot("step" + clicks);
			} else if (clicks < maxClicks) {
				clicks++;
				snapshot("click" + clicks);
				event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("monitor_up"), LuaValue.valueOf(0), LuaValue.valueOf(w * 0.6), LuaValue.valueOf(h * 0.6), LuaValue.valueOf(clicks), LuaValue.valueOf("left") });
			} else {
				// out of patience: press q (key event, then the char event ComputerCraft sends with it)
				clicks++;
				if (clicks % 2 == 0) event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("char"), LuaValue.valueOf("q"), LuaValue.valueOf("left") });
				else event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("key"), LuaValue.valueOf(16), LuaValue.valueOf(false), LuaValue.valueOf("left") });
				if (program.equals("tabletcam") && clicks == maxClicks + 1) {
					try {
						byte[] png = Files.readAllBytes(new File(luaDir, "tutorial_files/smile.png").toPath());
						LuaTable t = new LuaTable();
						for (int b = 0; b < png.length; b++) t.set(b + 1, LuaValue.valueOf(png[b] & 0xFF));
						event = LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("tablet_image"), t, LuaValue.valueOf("Tester"), LuaValue.valueOf("left") });
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (clicks > maxClicks + 30) throw new RuntimeException("program ignored q and clicks; stuck?");
			}
			result = co.resume(event);
		}
		if (!result.arg1().toboolean()) throw new RuntimeException(result.arg(2).tojstring());
		snapshot("end");
	}
}
