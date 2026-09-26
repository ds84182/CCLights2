package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Syntax-checks every shipped Lua program (port of the scratchpad LuaSyntax tool) with LuaJ 2.0. */
class LuaSyntaxTest {
	@Test
	void shippedProgramsParse() throws Exception {
		File dir = TestSupport.luaDir();
		File[] files = dir.listFiles(File::isFile);
		assertTrue(files != null && files.length > 0, "no Lua programs in " + dir);
		Arrays.sort(files);
		LuaTable g = JsePlatform.standardGlobals();
		List<String> bad = new ArrayList<>();
		for (File f : files) {
			try (InputStream in = new FileInputStream(f)) {
				LoadState.load(in, f.getName(), g);
				System.out.println("ok:   " + f.getName());
			} catch (Exception e) {
				System.out.println("FAIL: " + f.getName() + ": " + e.getMessage());
				bad.add(f.getName() + ": " + e.getMessage());
			}
		}
		assertTrue(bad.isEmpty(), "Lua syntax errors:\n" + String.join("\n", bad));
	}

	/**
	 * Every file under the mount must have a valid ResourceLocation path ([a-z0-9/._-]): Minecraft's
	 * resource packs silently skip anything else, so CC: Tweaked's mount would not contain it.
	 */
	@Test
	void mountedPathsAreValidResourceLocations() throws Exception {
		File dir = TestSupport.luaDir();
		List<String> bad = new ArrayList<>();
		try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(dir.toPath())) {
			walk.filter(java.nio.file.Files::isRegularFile).forEach(p -> {
				String rel = dir.toPath().relativize(p).toString().replace('\\', '/');
				if (!rel.matches("[a-z0-9/._-]+")) bad.add(rel);
			});
		}
		assertTrue(bad.isEmpty(), "Not valid resource paths (would be missing from /cclights2):\n" + String.join("\n", bad));
	}
}
