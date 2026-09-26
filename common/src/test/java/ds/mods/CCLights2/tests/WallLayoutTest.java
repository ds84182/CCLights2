package ds.mods.CCLights2.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import ds.mods.CCLights2.utils.WallLayout;

/** Deterministic scenarios and a fuzz test for the external-monitor wall layout. */
class WallLayoutTest {
	/** A plane of monitor blocks; place/remove run the same relayout the block entity runs. */
	static final class Plane implements WallLayout.Grid {
		static final class Block implements WallLayout.Cell {
			int xi, yi, w = 1, h = 1;

			@Override
			public int xIndex() { return xi; }

			@Override
			public int yIndex() { return yi; }

			@Override
			public int width() { return w; }

			@Override
			public int height() { return h; }

			@Override
			public void setLayout(int xIndex, int yIndex, int width, int height) {
				xi = xIndex;
				yi = yIndex;
				w = width;
				h = height;
			}

			@Override
			public String toString() { return "(" + xi + "," + yi + ") of " + w + "x" + h; }
		}

		final Map<Long, Block> blocks = new HashMap<>();
		final int maxW, maxH;
		int unloadedBelow = Integer.MIN_VALUE; // rows below this count as unloaded

		Plane(int maxW, int maxH) {
			this.maxW = maxW;
			this.maxH = maxH;
		}

		static long k(int x, int y) { return ((long) x << 32) | (y & 0xFFFFFFFFL); }

		@Override
		public WallLayout.Cell at(int gx, int gy) { return isLoaded(gx, gy) ? blocks.get(k(gx, gy)) : null; }

		@Override
		public boolean isLoaded(int gx, int gy) { return gy >= unloadedBelow; }

		@Override
		public int maxWidth() { return maxW; }

		@Override
		public int maxHeight() { return maxH; }

		Block place(int x, int y) {
			Block b = new Block();
			blocks.put(k(x, y), b);
			WallLayout.relayout(this, x, y);
			return b;
		}

		void remove(int x, int y) {
			blocks.remove(k(x, y));
			WallLayout.relayout(this, x, y);
		}

		Block get(int x, int y) { return blocks.get(k(x, y)); }

		/** Every wall in the plane as "x,y:WxH" of its origin, sorted. */
		List<String> walls() {
			List<String> out = new ArrayList<>();
			for (Map.Entry<Long, Block> e : blocks.entrySet()) {
				Block b = e.getValue();
				if (b.xi == 0 && b.yi == 0) out.add((int) (e.getKey() >> 32) + "," + (int) (long) e.getKey() + ":" + b.w + "x" + b.h);
			}
			out.sort(null);
			return out;
		}

		void assertConsistent(String when) {
			List<String> problems = WallLayout.problems(this, -20, -20, 40, 40);
			assertTrue(problems.isEmpty(), when + ": " + problems + " walls=" + walls());
			// maximal: no two aligned neighbours could still merge
			for (Map.Entry<Long, Block> e : blocks.entrySet()) {
				Block b = e.getValue();
				if (b.xi != 0 || b.yi != 0) continue;
				int x = (int) (e.getKey() >> 32), y = (int) (long) e.getKey();
				Block right = get(x + b.w, y);
				if (right != null && right.xi == 0 && right.yi == 0 && right.h == b.h && b.w + right.w <= maxW && isLoaded(x, y))
					throw new AssertionError(when + ": walls at " + x + "," + y + " and " + (x + b.w) + "," + y + " could merge horizontally; walls=" + walls());
				Block up = get(x, y + b.h);
				if (up != null && up.xi == 0 && up.yi == 0 && up.w == b.w && b.h + up.h <= maxH && isLoaded(x, y))
					throw new AssertionError(when + ": walls at " + x + "," + y + " and " + x + "," + (y + b.h) + " could merge vertically; walls=" + walls());
			}
		}
	}

	@Test
	void rowBuiltInAnyOrderBecomesOneWall() {
		int[][] orders = { { 0, 1, 2 }, { 2, 1, 0 }, { 1, 0, 2 }, { 0, 2, 1 } };
		for (int[] order : orders) {
			Plane p = new Plane(16, 9);
			for (int x : order) p.place(x, 0);
			p.assertConsistent("order " + order[0] + order[1] + order[2]);
			assertEquals(List.of("0,0:3x1"), p.walls());
		}
	}

	@Test
	void threeByTwoInEveryOrderThenSplitAndRejoin() {
		int[][] cells = { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 } };
		Random rnd = new Random(7);
		for (int round = 0; round < 50; round++) {
			Plane p = new Plane(16, 9);
			List<int[]> order = new ArrayList<>(List.of(cells));
			java.util.Collections.shuffle(order, rnd);
			for (int[] c : order) {
				p.place(c[0], c[1]);
				p.assertConsistent("round " + round + " after placing " + c[0] + "," + c[1]);
			}
			assertEquals(List.of("0,0:3x2"), p.walls(), "round " + round);

			p.remove(1, 0);
			p.assertConsistent("hole in the bottom row");
			assertEquals(List.of("0,0:1x1", "0,1:3x1", "2,0:1x1"), p.walls());
			p.place(1, 0);
			p.assertConsistent("hole filled");
			assertEquals(List.of("0,0:3x2"), p.walls());

			p.remove(0, 0);
			p.assertConsistent("origin removed");
			assertEquals(List.of("0,1:3x1", "1,0:2x1"), p.walls());
			p.place(0, 0);
			assertEquals(List.of("0,0:3x2"), p.walls());
		}
	}

	@Test
	void staleIndicesNeverProduceClaimsOverAir() {
		// A block whose stored layout is nonsense (as after an unload while neighbours changed)
		Plane p = new Plane(16, 9);
		Plane.Block a = p.place(0, 0);
		Plane.Block b = p.place(1, 0);
		a.setLayout(0, 0, 5, 2); // lies: claims a 5x2 wall
		b.setLayout(3, 1, 5, 2);
		p.place(2, 0);
		p.assertConsistent("after placing next to stale blocks");
		assertEquals(List.of("0,0:3x1"), p.walls());
	}

	@Test
	void sizeLimitsAreRespected() {
		Plane p = new Plane(3, 2);
		for (int x = 0; x < 7; x++) p.place(x, 0);
		p.assertConsistent("row of 7 with max width 3");
		for (String w : p.walls()) assertTrue(w.endsWith("x1"));
		int total = 0;
		for (String w : p.walls()) total += Integer.parseInt(w.substring(w.indexOf(':') + 1, w.indexOf('x')));
		assertEquals(7, total);
	}

	@Test
	void unloadedRowsAreLeftAlone() {
		Plane p = new Plane(16, 9);
		for (int y = 0; y < 3; y++) for (int x = 0; x < 2; x++) p.place(x, y);
		assertEquals(List.of("0,0:2x3"), p.walls());
		p.unloadedBelow = 1; // row 0 is now unloaded
		p.place(2, 2);       // touches the loaded part of the wall
		p.unloadedBelow = Integer.MIN_VALUE;
		p.assertConsistent("after a placement next to a partly unloaded wall");
		// the unloaded wall was kept intact, the new block stands alone
		assertEquals(List.of("0,0:2x3", "2,2:1x1"), p.walls());
	}

	@Test
	void fuzzRandomPlacementAndRemoval() {
		Random rnd = new Random(12345);
		for (int round = 0; round < 40; round++) {
			Plane p = new Plane(2 + rnd.nextInt(6), 1 + rnd.nextInt(4));
			for (int step = 0; step < 300; step++) {
				int x = rnd.nextInt(10), y = rnd.nextInt(6);
				if (p.get(x, y) == null || rnd.nextInt(3) == 0) {
					if (p.get(x, y) == null) p.place(x, y);
					else p.remove(x, y);
				} else {
					p.remove(x, y);
				}
				p.assertConsistent("round " + round + " step " + step + " at " + x + "," + y);
			}
		}
	}
}
