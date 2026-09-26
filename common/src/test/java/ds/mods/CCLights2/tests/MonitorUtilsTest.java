package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.api.lua.LuaException;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.utils.ImportFiles;
import ds.mods.CCLights2.utils.MonitorMath;
import ds.mods.CCLights2.utils.ScreenCodec;

/** The Minecraft-free helpers behind the monitor block entities and the GPU peripheral. */
class MonitorUtilsTest {
	@Test
	void pixelSizes() {
		assertEquals(64, MonitorMath.pixelSize(1, 64, 1));
		assertEquals(3 * 64 / 2, MonitorMath.pixelSize(3, 64, 2));
		assertEquals(16 * 64 / 8, MonitorMath.pixelSize(16, 64, 8));
		assertEquals(1, MonitorMath.pixelSize(1, 8, 64), "never below one pixel");
		assertEquals(64, MonitorMath.pixelSize(1, 64, 0), "scale 0 treated as 1");
	}

	@Test
	void alongRightFollowsTheViewersRightHand() {
		// Right hand towards +x: the fraction is x itself; towards -x it is mirrored. Same for z.
		assertEquals(0.25, MonitorMath.alongRight(0.25, 0.9, 1, 0), 1e-9);
		assertEquals(0.75, MonitorMath.alongRight(0.25, 0.9, -1, 0), 1e-9);
		assertEquals(0.9, MonitorMath.alongRight(0.25, 0.9, 0, 1), 1e-9);
		assertEquals(0.1, MonitorMath.alongRight(0.25, 0.9, 0, -1), 1e-9);
		assertEquals(1.0, MonitorMath.alongRight(1.5, 0, 1, 0), 1e-9, "clamped");
	}

	@Test
	void hitToPixelOnASingleMonitor() {
		// Top-left, centre and bottom-right of a 256x144 screen.
		assertArrayEquals(new int[] { 0, 0 }, MonitorMath.hitToPixel(0, 0, 1, 1, 256, 144, 0.0, 1.0));
		assertArrayEquals(new int[] { 128, 72 }, MonitorMath.hitToPixel(0, 0, 1, 1, 256, 144, 0.5, 0.5));
		assertArrayEquals(new int[] { 255, 143 }, MonitorMath.hitToPixel(0, 0, 1, 1, 256, 144, 1.0, 0.0));
	}

	@Test
	void hitToPixelOnAWall() {
		// 3x2 wall, 64 px per block at scale 1: 192x128. Block (2, 1) is the top-right block.
		int w = MonitorMath.pixelSize(3, 64, 1), h = MonitorMath.pixelSize(2, 64, 1);
		assertArrayEquals(new int[] { 128, 0 }, MonitorMath.hitToPixel(2, 1, 3, 2, w, h, 0.0, 1.0));
		assertArrayEquals(new int[] { 191, 63 }, MonitorMath.hitToPixel(2, 1, 3, 2, w, h, 0.999, 0.001));
		// The origin (0, 0) is the bottom-left block: its top-left corner is pixel (0, 64).
		assertArrayEquals(new int[] { 0, 64 }, MonitorMath.hitToPixel(0, 0, 3, 2, w, h, 0.0, 1.0));
		assertArrayEquals(new int[] { 96, 96 }, MonitorMath.hitToPixel(1, 0, 3, 2, w, h, 0.5, 0.5));
		// Scale 2 halves the resolution.
		int w2 = MonitorMath.pixelSize(3, 64, 2), h2 = MonitorMath.pixelSize(2, 64, 2);
		assertArrayEquals(new int[] { 48, 48 }, MonitorMath.hitToPixel(1, 0, 3, 2, w2, h2, 0.5, 0.5));
	}

	@Test
	void pngRoundTripKeepsPixelsExactly() {
		Texture t = new Texture(37, 21);
		int[] px = new int[37 * 21];
		for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | ((i * 0x9E3779B1) & 0xFFFFFF);
		px[5] = 0x80FF0000; // translucent pixel survives too
		t.setPixels(0, 0, 37, 21, px);

		byte[] png = ScreenCodec.encodePng(t);
		assertNotNull(png);
		Texture back = ScreenCodec.decodeTexture(png);
		assertNotNull(back);
		assertEquals(37, back.getWidth());
		assertEquals(21, back.getHeight());
		assertArrayEquals(px, back.getPixels(0, 0, 37, 21));

		// Into an existing (smaller) monitor screen: copied into the top-left corner, cut off at the edge.
		Monitor m = new Monitor(16, 8, null);
		ScreenCodec.copyInto(m.tex, ScreenCodec.decode(png));
		assertEquals(px[0], m.tex.getRGB(0, 0));
		assertEquals(px[7 * 37 + 15], m.tex.getRGB(15, 7));
		assertNotNull(m.tex.getRgbCache(), "published to the render cache");

		assertNull(ScreenCodec.decode(new byte[] { 1, 2, 3 }));
		assertNull(ScreenCodec.decode(null));
	}

	@Test
	void importFilesStayInsideTheComputerFolder(@TempDir Path world) throws Exception {
		Path dir = ImportFiles.computerDir(world, 7);
		assertEquals(world.resolve("computercraft/computer/7"), dir);
		Files.createDirectories(dir.resolve("images"));
		Files.write(dir.resolve("images/a.png"), "abc".getBytes(StandardCharsets.UTF_8));
		Files.write(world.resolve("secret.txt"), "no".getBytes(StandardCharsets.UTF_8));

		assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), ImportFiles.read(dir, "import", "images/a.png"));
		assertThrows(LuaException.class, () -> ImportFiles.read(dir, "import", "missing.png"));
		assertThrows(LuaException.class, () -> ImportFiles.read(dir, "import", "../../../secret.txt"));
		assertThrows(LuaException.class, () -> ImportFiles.read(dir, "import", "images"));
	}
}
