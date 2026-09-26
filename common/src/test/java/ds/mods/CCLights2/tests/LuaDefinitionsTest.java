package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.gpu.GpuLuaApi;

/**
 * The IDE definition file (docs/ide/cclights2.lua) must describe exactly the methods the GPU peripheral
 * exposes: every Lua method (and alias) needs a {@code function GPU.<name>(...)} entry and nothing else
 * may be listed as a GPU method.
 */
class LuaDefinitionsTest {
	private static File definitions() {
		File[] candidates = { new File("../docs/ide/cclights2.lua"), new File("docs/ide/cclights2.lua") };
		for (File f : candidates) if (f.isFile()) return f;
		throw new IllegalStateException("docs/ide/cclights2.lua not found from " + new File(".").getAbsolutePath());
	}

	@Test
	void everyGpuMethodHasADefinitionAndViceVersa() throws Exception {
		String text = new String(Files.readAllBytes(definitions().toPath()), StandardCharsets.UTF_8);
		Set<String> defined = new LinkedHashSet<>();
		Matcher m = Pattern.compile("^function GPU\\.([A-Za-z_][A-Za-z0-9_]*)\\(", Pattern.MULTILINE).matcher(text);
		while (m.find()) defined.add(m.group(1));

		Set<String> real = new LinkedHashSet<>(List.of(GpuLuaApi.METHOD_NAMES));
		List<String> missing = new ArrayList<>();
		for (String name : real) if (!defined.contains(name)) missing.add(name);
		List<String> extra = new ArrayList<>();
		for (String name : defined) if (!real.contains(name)) extra.add(name);

		assertTrue(missing.isEmpty(), "GPU methods without an IDE definition: " + missing);
		assertTrue(extra.isEmpty(), "IDE definitions for methods the GPU does not have: " + extra);
		// aliases are part of the method table too; make sure the table still carries them
		for (String[] alias : CommandEnum.ALIASES) assertTrue(real.contains(alias[0]), "alias " + alias[0] + " missing from the method table");
	}

	@Test
	void screenObjectsAreDefined() throws Exception {
		String text = new String(Files.readAllBytes(definitions().toPath()), StandardCharsets.UTF_8);
		for (String fn : new String[] { "Screen.getResolution", "Screen.getSize", "Screen.getType", "Screen.getPosition",
				"ExternalMonitor.getDPM", "ExternalMonitor.getBlockResolution", "ExternalMonitor.setScale", "ExternalMonitor.getScale",
				"TabletTransceiver.getResolution", "TabletTransceiver.getNumberOfTablets", "TabletTransceiver.getTabletUUID", "TabletTransceiver.disconnect",
				"CanvasLib.fit", "Canvas.apply", "Canvas.toCanvas", "Canvas.text", "Canvas.clear", "Canvas.refresh" }) {
			assertTrue(text.contains("function " + fn + "("), "definition missing: " + fn);
		}
		for (String event : new String[] { "monitor_down", "monitor_move", "monitor_up", "monitor_scroll", "key", "key_up", "char", "tablet_image" }) {
			assertTrue(text.contains("\"" + event + "\""), "event missing from CCLights2.Event: " + event);
		}
	}
}
