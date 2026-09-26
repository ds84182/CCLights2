package ds.mods.CCLights2.tests;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.DrawState;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.Serialize;

/**
 * Headless exercise of the MC-independent parts: font, primitives, blend modes, command
 * replication through the serializer, and the client replica ending up with identical pixels.
 */
class GpuSmokeTest {
	private final TestSupport.Checks checks = new TestSupport.Checks();

	private void check(boolean ok, String what) {
		checks.check(ok, what);
	}

	@Test
	void smoke() throws Exception {
		String outDir = TestSupport.outDir("gpu").getPath();
		TestSupport.loadFont();
		check(Texture.isFontLoaded(), "font loaded");
		check(Texture.getCharWidth('A') == 6, "width of 'A' is 6 (got " + Texture.getCharWidth('A') + ")");
		check(Texture.getCharWidth('i') == 2, "width of 'i' is 2 (got " + Texture.getCharWidth('i') + ")");
		check(Texture.getCharWidth(' ') == 4, "width of space is 4");
		check(Texture.getStringWidth("Hello") == 6 + 6 + 3 + 3 + 6, "string width of Hello (got " + Texture.getStringWidth("Hello") + ")");
		check(Texture.glyphIndex('A') == 65 && Texture.glyphIndex('~') == 126, "ASCII glyph indices line up with code points");

		// --- server GPU with a monitor
		GPU server = new GPU(8192);
		Monitor mon = new Monitor(256, 144, null);
		mon.addGPU(server);
		check(server.bindedTexture == mon.tex && server.bindedSlot == 0, "monitor bound as slot 0");

		List<DrawCMD> script = new ArrayList<DrawCMD>();
		script.add(new DrawCMD(CommandEnum.SetColor, 30, 30, 60, 255));
		script.add(new DrawCMD(CommandEnum.Fill));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 255, 255, 255));
		script.add(new DrawCMD(CommandEnum.DrawText, "Hello, CCLights2! éü", 4, 4));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 80, 80, 255));
		script.add(new DrawCMD(CommandEnum.FilledRectangle, 10, 20, 40, 30));
		script.add(new DrawCMD(CommandEnum.SetColor, 80, 255, 120, 255));
		script.add(new DrawCMD(CommandEnum.FilledOval, 60, 20, 40, 30));
		script.add(new DrawCMD(CommandEnum.SetColor, 80, 160, 255, 255));
		script.add(new DrawCMD(CommandEnum.FilledTriangle, 110, 50, 130, 20, 150, 50));
		script.add(new DrawCMD(CommandEnum.SetLineWidth, 3.0));
		script.add(new DrawCMD(CommandEnum.SetAntialias, true));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 255, 0, 255));
		script.add(new DrawCMD(CommandEnum.Curve, 10.0, 100.0, 128.0, 40.0, 246.0, 100.0));
		script.add(new DrawCMD(CommandEnum.SetAntialias, false));
		script.add(new DrawCMD(CommandEnum.SetLineWidth, 1.0));
		script.add(new DrawCMD(CommandEnum.SetBlendMode, "add"));
		script.add(new DrawCMD(CommandEnum.SetColor, 0, 0, 120, 255));
		script.add(new DrawCMD(CommandEnum.FilledRectangle, 0, 60, 256, 20));
		script.add(new DrawCMD(CommandEnum.SetBlendMode, "normal"));
		script.add(new DrawCMD(CommandEnum.Push));
		script.add(new DrawCMD(CommandEnum.Translate, 200.0, 100.0));
		script.add(new DrawCMD(CommandEnum.Rotate, 0.6));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 255, 255, 255));
		script.add(new DrawCMD(CommandEnum.DrawText, "rotated", 0, 0));
		script.add(new DrawCMD(CommandEnum.Pop));
		script.add(new DrawCMD(CommandEnum.SetColor, 200, 100, 255, 255));
		script.add(new DrawCMD(CommandEnum.Polygon, (Object) new int[] { 170, 110, 200, 130, 180, 140, 160, 130 }));
		script.add(new DrawCMD(CommandEnum.GradientRectangle, 10, 120, 100, 20, 255, 255, 255, 255, false));
		script.add(new DrawCMD(CommandEnum.CreateTexture, 16, 16));
		script.add(new DrawCMD(CommandEnum.BindTexture, 1));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 0, 0, 255));
		script.add(new DrawCMD(CommandEnum.Fill));
		script.add(new DrawCMD(CommandEnum.SetColor, 0, 0, 255, 255));
		script.add(new DrawCMD(CommandEnum.FilledRectangle, 0, 0, 8, 8));
		script.add(new DrawCMD(CommandEnum.BindTexture, 0));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 255, 255, 255));
		script.add(new DrawCMD(CommandEnum.DrawTexture, 1, 220, 10));
		script.add(new DrawCMD(CommandEnum.FlipVertically, 1));
		script.add(new DrawCMD(CommandEnum.DrawTexture, 1, 238, 10));
		script.add(new DrawCMD(CommandEnum.DrawTextureScaled, 1, 220, 30, 32, 16));
		script.add(new DrawCMD(CommandEnum.SetColor, 0, 255, 0, 255));
		script.add(new DrawCMD(CommandEnum.DrawTexture, 1, 220, 50));
		script.add(new DrawCMD(CommandEnum.SetColor, 255, 255, 255, 255));
		script.add(new DrawCMD(CommandEnum.SetPixels, 2, 2, 120, 120, new int[] { 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF }));

		// run on the server, serialize each command as it was sent
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeInt(script.size());
		for (DrawCMD c : script) {
			server.processCommand(c);
			Serialize.writeCommand(out, c);
		}
		// createTexture on the server must have pinned the id into the replicated command
		check(script.get(29).args.length == 3 && ((Integer) script.get(29).args[2]) == 1, "server appended allocated texture id to createTexture");

		// --- replay on a "client" GPU from the wire bytes
		GPU client = new GPU(8192);
		client.server = false;
		Monitor cmon = new Monitor(256, 144, null);
		cmon.addGPU(client);
		ByteArrayDataInput in = ByteStreams.newDataInput(out.toByteArray());
		int n = in.readInt();
		for (int i = 0; i < n; i++) client.processCommand(Serialize.readCommand(in));

		int[] a = mon.tex.getPixels(0, 0, 256, 144);
		int[] b = cmon.tex.getPixels(0, 0, 256, 144);
		int diff = 0;
		for (int i = 0; i < a.length; i++) if (a[i] != b[i]) diff++;
		check(diff == 0, "client replica pixel-identical to server (" + diff + " differing pixels)");

		// text actually rendered something white-ish near 4,4
		boolean textDrawn = false;
		for (int y = 4; y < 12 && !textDrawn; y++) for (int x = 4; x < 60; x++) if ((mon.tex.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) { textDrawn = true; break; }
		check(textDrawn, "drawText produced white pixels");
		check((mon.tex.getRGB(20, 30) & 0xFFFFFF) == 0xFF5050, "filledRectangle colour at (20,30)");
		check((mon.tex.getRGB(120, 120) & 0xFFFFFF) == 0xFF0000 && (mon.tex.getRGB(121, 121) & 0xFFFFFF) == 0xFFFFFF, "setPixels row-major placement");
		int add = mon.tex.getRGB(5, 70) & 0xFFFFFF;
		check(add == 0x1E1EB4, "additive blend: (30,30,60)+(0,0,120) = 1E1EB4 (got " + Integer.toHexString(add) + ")");
		check((mon.tex.getRGB(221, 11) & 0xFFFFFF) == 0x0000FF, "drawTexture placed sub-texture (blue corner top-left)");
		check((mon.tex.getRGB(239, 11) & 0xFFFFFF) == 0xFF0000 && (mon.tex.getRGB(239, 24) & 0xFFFFFF) == 0x0000FF, "flipVertically moved blue corner to the bottom");
		check(server.getUsedMemory() == Texture.memoryUseFor(16, 16), "memory accounting after createTexture");
		int tinted = mon.tex.getRGB(230, 60) & 0xFFFFFF;
		check(tinted == 0x000000 || tinted == 0x00FF00, "tinted drawTexture multiplies colours (got " + Integer.toHexString(tinted) + ")");

		// state sync round trip
		ByteArrayDataOutput so = ByteStreams.newDataOutput();
		server.state.write(so);
		DrawState copy = new DrawState();
		copy.read(ByteStreams.newDataInput(so.toByteArray()));
		check(copy.color.equals(server.state.color) && copy.lineWidth == server.state.lineWidth && copy.blend == server.state.blend, "DrawState survives serialization");

		// errors
		try {
			server.processCommand(new DrawCMD(CommandEnum.FreeTexture, 0));
			check(false, "freeing slot 0 must fail");
		} catch (Exception e) {
			check(true, "freeing slot 0 rejected: " + e.getMessage());
		}
		try {
			server.processCommand(new DrawCMD(CommandEnum.CreateTexture, 4096, 4096));
			check(false, "oversized texture must fail on memory");
		} catch (Exception e) {
			check(true, "oversized texture rejected: " + e.getMessage());
		}
		try {
			server.processCommand(new DrawCMD(CommandEnum.Pop));
			check(false, "pop on empty stack must fail");
		} catch (Exception e) {
			check(true, "pop on empty stack rejected");
		}

		// texture-wide filters must not throw
		server.processCommand(new DrawCMD(CommandEnum.CopyTexture, 0));
		int copyId = server.bindedSlot == 0 ? 2 : server.bindedSlot;
		server.processCommand(new DrawCMD(CommandEnum.Blur, copyId, 3));
		server.processCommand(new DrawCMD(CommandEnum.GaussianBlur, copyId, 2.0));
		server.processCommand(new DrawCMD(CommandEnum.Glow, copyId, 1.0));
		server.processCommand(new DrawCMD(CommandEnum.Sharpen, copyId, 0.5));
		server.processCommand(new DrawCMD(CommandEnum.Invert, copyId));
		server.processCommand(new DrawCMD(CommandEnum.Grayscale, copyId));
		server.processCommand(new DrawCMD(CommandEnum.ResizeTexture, copyId, 128, 72, true));
		check(server.textures[copyId].getWidth() == 128, "resizeTexture applied");

		ImageIO.write(mon.tex.getImage(), "png", new File(outDir, "server.png"));
		ImageIO.write(cmon.tex.getImage(), "png", new File(outDir, "client.png"));
		ImageIO.write(server.textures[copyId].getImage(), "png", new File(outDir, "filtered.png"));

		// texUpdate publishes only on change
		mon.tex.texUpdate();
		int v = mon.tex.getCacheVersion();
		mon.tex.texUpdate();
		check(mon.tex.getCacheVersion() == v, "texUpdate is a no-op without changes");
		server.processCommand(new DrawCMD(CommandEnum.Plot, 1, 1));
		mon.tex.texUpdate();
		check(mon.tex.getCacheVersion() != v, "texUpdate publishes after a draw");

		checks.assertAllPassed();
	}
}
