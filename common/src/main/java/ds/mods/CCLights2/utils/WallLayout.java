package ds.mods.CCLights2.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Lays out external-monitor walls in a plane. Minecraft-free so it can be fuzz-tested.
 * <p>
 * Every block stores its wall as {@code xIndex, yIndex, width, height}; the block at index (0,0) is the
 * origin. {@link #relayout} is called after a block at (gx, gy) was placed or removed and rebuilds the
 * walls around it from what is actually there: walls that are still complete and consistent are kept as
 * units, every other block becomes a 1x1 unit, then units merge greedily (rows first, then columns)
 * while the size limits allow. Stored indices are never trusted beyond "is this wall still intact", so
 * a wall can never claim blocks that do not exist, whatever order the blocks were placed or broken in.
 */
public final class WallLayout {
	private WallLayout() {}

	/** A monitor block. Plane coordinates are tracked by the algorithm, not the cell. */
	public interface Cell {
		int xIndex();

		int yIndex();

		int width();

		int height();

		void setLayout(int xIndex, int yIndex, int width, int height);
	}

	/** The plane of one facing: gx grows to the viewer's right, gy grows upwards. */
	public interface Grid {
		/** The monitor block at plane coordinates, or null when there is none there. */
		@Nullable
		Cell at(int gx, int gy);

		/** False when the position is not loaded; walls reaching into unloaded space are left alone. */
		boolean isLoaded(int gx, int gy);

		int maxWidth();

		int maxHeight();
	}

	/** The outcome of a relayout: origins of the resulting walls and every cell whose layout changed. */
	public static final class Result {
		/** Bottom-left cell of each resulting wall, with its plane coordinates. */
		public final List<Placed> origins = new ArrayList<>();
		public final Set<Cell> changed = new LinkedHashSet<>();
	}

	/** A cell and where it is in the plane. */
	public record Placed(Cell cell, int gx, int gy) {}

	/** A rectangle of cells in the plane, bottom-left at (x, y). */
	private static final class Unit {
		int x, y, w, h;
		boolean loaded = true; // false when some of its cells are in unloaded space: never merged

		Unit(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}
	}

	/**
	 * Rebuilds the walls around plane position (gx, gy) after the block there was placed or removed.
	 * Only cells connected (4-neighbourhood) to that position within one wall size are considered, plus
	 * whole walls they belong to.
	 */
	public static Result relayout(Grid grid, int gx, int gy) {
		int maxW = Math.max(1, grid.maxWidth());
		int maxH = Math.max(1, grid.maxHeight());
		int minX = gx - maxW, maxX = gx + maxW, minY = gy - maxH, maxY = gy + maxH;

		// 1. the connected component around the change, inside the search box
		Map<Long, Cell> cells = new HashMap<>();
		Deque<long[]> todo = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		int[][] starts = { { gx, gy }, { gx - 1, gy }, { gx + 1, gy }, { gx, gy - 1 }, { gx, gy + 1 } };
		for (int[] s : starts) todo.add(new long[] { s[0], s[1] });
		while (!todo.isEmpty()) {
			long[] p = todo.poll();
			int x = (int) p[0], y = (int) p[1];
			if (x < minX || x > maxX || y < minY || y > maxY || !seen.add(key(x, y))) continue;
			Cell c = grid.at(x, y);
			if (c == null) continue;
			cells.put(key(x, y), c);
			todo.add(new long[] { x - 1, y });
			todo.add(new long[] { x + 1, y });
			todo.add(new long[] { x, y - 1 });
			todo.add(new long[] { x, y + 1 });
		}

		// 2. units: intact walls stay whole, everything else is a single block
		List<Unit> units = new ArrayList<>();
		Set<Long> covered = new HashSet<>();
		for (Map.Entry<Long, Cell> e : cells.entrySet()) {
			long k = e.getKey();
			if (covered.contains(k)) continue;
			int x = (int) (k >> 32), y = (int) k;
			Unit u = intactWall(grid, e.getValue(), x, y);
			if (u == null) u = new Unit(x, y, 1, 1);
			for (int yy = u.y; yy < u.y + u.h; yy++) for (int xx = u.x; xx < u.x + u.w; xx++) covered.add(key(xx, yy));
			units.add(u);
		}

		// 3. greedy merging: rows first, then columns, until nothing fits any more
		boolean merged = true;
		while (merged) {
			merged = mergePass(units, true, maxW, maxH) || mergePass(units, false, maxW, maxH);
		}

		// 4. write the layout back
		Result result = new Result();
		units.sort(Comparator.<Unit>comparingInt(u -> u.y).thenComparingInt(u -> u.x));
		for (Unit u : units) {
			Cell origin = null;
			for (int yy = 0; yy < u.h; yy++) {
				for (int xx = 0; xx < u.w; xx++) {
					Cell c = grid.at(u.x + xx, u.y + yy);
					if (c == null) continue; // unloaded part of a kept wall
					if (xx == 0 && yy == 0) origin = c;
					if (c.xIndex() != xx || c.yIndex() != yy || c.width() != u.w || c.height() != u.h) {
						c.setLayout(xx, yy, u.w, u.h);
						result.changed.add(c);
					}
				}
			}
			if (origin != null) result.origins.add(new Placed(origin, u.x, u.y));
		}
		return result;
	}

	/**
	 * The wall {@code c} (at x, y) claims to be part of, when it is intact: its origin exists at the
	 * claimed offset, and every cell of the rectangle exists with matching indices (cells in unloaded
	 * space are assumed present and mark the unit as unmergeable). Null when anything disagrees.
	 */
	@Nullable
	private static Unit intactWall(Grid grid, Cell c, int x, int y) {
		int w = c.width(), h = c.height();
		if (w < 1 || h < 1 || c.xIndex() < 0 || c.yIndex() < 0 || c.xIndex() >= w || c.yIndex() >= h) return null;
		if (w > grid.maxWidth() || h > grid.maxHeight()) return null;
		int ox = x - c.xIndex(), oy = y - c.yIndex();
		Unit u = new Unit(ox, oy, w, h);
		for (int yy = 0; yy < h; yy++) {
			for (int xx = 0; xx < w; xx++) {
				if (!grid.isLoaded(ox + xx, oy + yy)) {
					u.loaded = false;
					continue;
				}
				Cell m = grid.at(ox + xx, oy + yy);
				if (m == null || m.xIndex() != xx || m.yIndex() != yy || m.width() != w || m.height() != h) return null;
			}
		}
		return u;
	}

	/** One merge pass; horizontal joins side-by-side units of equal height, vertical stacked units of equal width. */
	private static boolean mergePass(List<Unit> units, boolean horizontal, int maxW, int maxH) {
		units.sort(Comparator.<Unit>comparingInt(u -> u.y).thenComparingInt(u -> u.x));
		for (int i = 0; i < units.size(); i++) {
			Unit a = units.get(i);
			if (!a.loaded) continue;
			for (int j = 0; j < units.size(); j++) {
				if (i == j) continue;
				Unit b = units.get(j);
				if (!b.loaded) continue;
				boolean fits;
				if (horizontal) fits = b.y == a.y && b.h == a.h && b.x == a.x + a.w && a.w + b.w <= maxW;
				else fits = b.x == a.x && b.w == a.w && b.y == a.y + a.h && a.h + b.h <= maxH;
				if (!fits) continue;
				if (horizontal) a.w += b.w;
				else a.h += b.h;
				units.remove(j);
				return true;
			}
		}
		return false;
	}

	private static long key(int x, int y) {
		return ((long) x << 32) | (y & 0xFFFFFFFFL);
	}

	/** Checks a plane for consistency (every cell's wall intact, no overlaps); for tests and diagnostics. */
	public static List<String> problems(Grid grid, int minX, int minY, int maxX, int maxY) {
		List<String> out = new ArrayList<>();
		Map<Long, Long> owner = new HashMap<>();
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				Cell c = grid.at(x, y);
				if (c == null) continue;
				Unit u = intactWall(grid, c, x, y);
				if (u == null) {
					out.add("cell " + x + "," + y + " claims " + c.width() + "x" + c.height() + " at (" + c.xIndex() + "," + c.yIndex() + ") but that wall is not intact");
					continue;
				}
				long o = key(u.x, u.y);
				Long prev = owner.put(key(x, y), o);
				if (prev != null && prev != o) out.add("cell " + x + "," + y + " belongs to two walls");
			}
		}
		return Collections.unmodifiableList(out);
	}
}
