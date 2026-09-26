package ds.mods.CCLights2.utils;

/**
 * Minecraft-free geometry of monitor screens: pixel sizes of external monitor walls and the mapping
 * from a hit on a screen face to a screen pixel. Kept separate so it can be unit tested headless.
 * <p>
 * Conventions (shared with the renderer): a wall's block (xIndex, yIndex) counts xIndex towards the
 * viewer's right hand when looking at the screen and yIndex upwards; the origin (0, 0) is the
 * bottom-left block as the viewer sees it. Screen pixel (0, 0) is the top-left corner of the wall.
 */
public final class MonitorMath {
	private MonitorMath() {}

	/** Pixel extent of {@code blocks} blocks at {@code pixelsPerBlock}, divided by the scale; at least 1. */
	public static int pixelSize(int blocks, int pixelsPerBlock, int scale) {
		return Math.max(1, blocks * pixelsPerBlock / Math.max(1, scale));
	}

	public static int clamp(int v, int min, int max) {
		return v < min ? min : (v > max ? max : v);
	}

	private static double clamp01(double v) {
		if (Double.isNaN(v)) return 0;
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	/**
	 * Fraction (0..1) of a hit along the viewer's right-hand axis within one block.
	 * @param lx hit x relative to the block's minimum corner
	 * @param lz hit z relative to the block's minimum corner
	 * @param rightStepX x step of the viewer's right-hand direction (-1, 0 or 1)
	 * @param rightStepZ z step of the viewer's right-hand direction (-1, 0 or 1)
	 */
	public static double alongRight(double lx, double lz, int rightStepX, int rightStepZ) {
		if (rightStepX > 0) return clamp01(lx);
		if (rightStepX < 0) return clamp01(1 - lx);
		if (rightStepZ > 0) return clamp01(lz);
		return clamp01(1 - lz);
	}

	/**
	 * Converts a hit on block (xIndex, yIndex) of a widthBlocks x heightBlocks wall showing a
	 * pixelW x pixelH screen into screen pixel coordinates, clamped to the screen.
	 * @param along fraction 0..1 of the hit along the viewer's right-hand axis within the block
	 * @param up fraction 0..1 of the hit from the block's bottom
	 * @return {x, y}
	 */
	public static int[] hitToPixel(int xIndex, int yIndex, int widthBlocks, int heightBlocks, int pixelW, int pixelH, double along, double up) {
		widthBlocks = Math.max(1, widthBlocks);
		heightBlocks = Math.max(1, heightBlocks);
		pixelW = Math.max(1, pixelW);
		pixelH = Math.max(1, pixelH);
		double fx = (xIndex + clamp01(along)) / widthBlocks;
		double fy = ((heightBlocks - 1 - yIndex) + (1 - clamp01(up))) / heightBlocks;
		int px = clamp((int) Math.floor(fx * pixelW), 0, pixelW - 1);
		int py = clamp((int) Math.floor(fy * pixelH), 0, pixelH - 1);
		return new int[] { px, py };
	}
}
