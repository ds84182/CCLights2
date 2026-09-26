package ds.mods.CCLights2.client.render;

import java.awt.Color;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import ds.mods.CCLights2.client.TabletLink;
import ds.mods.CCLights2.gpu.DrawState;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.item.TabletItem;
import net.minecraft.world.item.ItemStack;

/**
 * The fixed pictures a tablet shows when it has nothing live to display (port of the 1.7.10
 * TabletRenderer default/error textures), and the choice between those and the paired screen.
 */
public final class TabletTextures {
	private TabletTextures() {}

	private static Texture notPaired;
	private static Texture noSignal;
	private static Texture body;
	private static Texture bezel;

	/** Dark plastic for the tablet item's back and edges. */
	public static synchronized Texture body() {
		if (body == null) body = solid(new Color(28, 28, 32));
		return body;
	}

	/** Slightly lighter frame around the tablet item's screen. */
	public static synchronized Texture bezel() {
		if (bezel == null) bezel = solid(new Color(48, 48, 54));
		return bezel;
	}

	private static Texture solid(Color c) {
		Texture t = new Texture(4, 4);
		t.setWantCache(true);
		t.fill(c);
		t.texUpdate();
		return t;
	}

	/** Shown on an unpaired tablet. */
	public static synchronized Texture notPaired() {
		if (notPaired == null) {
			notPaired = make(new Color(20, 40, 90),
					"Not paired.",
					"Right click a Tablet Transceiver with this tablet to pair it.",
					"Then right click the tablet to open its screen.");
		}
		return notPaired;
	}

	/** Shown when the paired transceiver is not loaded or out of range. */
	public static synchronized Texture noSignal() {
		if (noSignal == null) {
			noSignal = make(new Color(120, 20, 20),
					"No signal.",
					"Move closer to the transceiver this tablet is paired with.",
					null);
		}
		return noSignal;
	}

	private static Texture make(Color bg, String l1, String l2, @Nullable String l3) {
		Texture t = new Texture(TabletTransceiverBlockEntity.WIDTH, TabletTransceiverBlockEntity.HEIGHT);
		t.setWantCache(true);
		t.fill(bg);
		DrawState s = new DrawState();
		s.color = Color.white;
		t.drawText(s, l1, 8, 8);
		t.drawText(s, l2, 8, 20);
		if (l3 != null) t.drawText(s, l3, 8, 30);
		t.texUpdate();
		return t;
	}

	/** The live screen of the paired transceiver when it is loaded and in range, else null. */
	@Nullable
	public static Texture liveScreen(ItemStack stack) {
		TabletTransceiverBlockEntity tile = TabletLink.findTransceiver(stack);
		if (tile == null || !TabletLink.inRange(tile)) return null;
		Monitor mon = tile.getMonitor();
		return mon == null ? null : mon.tex;
	}

	/** What a tablet shows: the paired screen, the pairing hint or the no-signal picture. */
	public static Texture screenFor(ItemStack stack) {
		if (TabletItem.getTransceiverId(stack) == null) return notPaired();
		Texture live = liveScreen(stack);
		return live == null ? noSignal() : live;
	}
}
