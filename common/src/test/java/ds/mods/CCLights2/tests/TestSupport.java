package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ds.mods.CCLights2.gpu.Texture;

/** Shared paths and a tiny check collector for the ported scratchpad tests. */
final class TestSupport {
	private TestSupport() {}

	/** The shipped Lua programs (common data/cclights/lua, what the CC:T resource mount serves). */
	static File luaDir() {
		File f = new File("src/main/resources/data/cclights/lua");
		if (new File(f, "gpudemo").isFile()) return f.getAbsoluteFile();
		throw new IllegalStateException("Lua program directory not found (working dir " + new File(".").getAbsolutePath() + ")");
	}

	/** Output directory for rendered frames (build/test-output/<name>). */
	static File outDir(String name) {
		String base = System.getProperty("cclights.testOut", "build/test-output");
		File dir = new File(base, name);
		dir.mkdirs();
		return dir;
	}

	/** A file from src/test/resources. */
	static File resource(String path) {
		URL url = TestSupport.class.getResource("/" + path);
		if (url == null) throw new IllegalStateException("missing test resource " + path);
		try {
			return new File(url.toURI());
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	static void loadFont() throws IOException {
		try (InputStream in = TestSupport.class.getResourceAsStream("/ascii.png")) {
			if (in == null) throw new IllegalStateException("missing test resource ascii.png");
			Texture.loadFont(in);
		}
	}

	/** Collects failed checks so one test reports all of them, like the original main() programs did. */
	static final class Checks {
		final List<String> failures = new ArrayList<>();

		void check(boolean ok, String what) {
			System.out.println((ok ? "ok:   " : "FAIL: ") + what);
			if (!ok) failures.add(what);
		}

		void assertAllPassed() {
			assertTrue(failures.isEmpty(), failures.size() + " check(s) failed:\n" + String.join("\n", failures));
		}
	}
}
