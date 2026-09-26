package ds.mods.CCLights2.block.entity;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.utils.MonitorLocks;
import ds.mods.CCLights2.utils.MonitorMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One block of a multi-block external monitor (port of TileEntityExternalMonitor). Adjacent blocks
 * facing the same way merge into a rectangle; the block at index (0,0), the origin, owns the shared
 * {@link Monitor} and renders it.
 * <p>
 * Layout: xIndex grows towards the viewer's right hand when looking at the screen
 * ({@link #getRightDirection()}), yIndex grows upwards, so the origin is the bottom-left block as the
 * viewer sees it. Screen pixel (0,0) is the top-left corner of the whole wall.
 * <p>
 * Merging runs on the server only. Clients receive width/height/index/scale through the update tag
 * and look the origin up lazily, caching the reference so rendering never has to walk the world.
 */
public class ExternalMonitorBlockEntity extends MonitorBlockEntity {
	public static final int MAX_SCALE = 8;
	private static final int MAX_MERGE_STEPS = 64;

	private int width = 1;
	private int height = 1;
	private int xIndex = 0;
	private int yIndex = 0;
	/** Pixel magnification: the screen has pixelsPerBlock() / scale pixels per block. */
	private int scale = 1;
	private boolean destroyed = false;
	private boolean ignoreMe = false;
	private boolean dirty = false;

	/** Screen pixels read from NBT before this block's screen existed. */
	@Nullable
	private byte[] pendingScreen;
	@Nullable
	private ExternalMonitorBlockEntity cachedOrigin;
	@Nullable
	private AABB renderBox;

	public ExternalMonitorBlockEntity(BlockPos pos, BlockState state) {
		super(Registration.EXTERNAL_MONITOR_BE.get(), pos, state);
	}

	/** Screen pixels per block before scaling, from the config. */
	public static int pixelsPerBlock() {
		return Config.externalMonitorPixelsPerBlock;
	}

	/** Server ticker installed by ExternalMonitorBlock#getTicker: pushes structure changes to clients. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, ExternalMonitorBlockEntity be) {
		if (be.dirty) {
			be.dirty = false;
			be.sendUpdate();
		}
	}

	// ------------------------------------------------------------------ geometry

	public boolean isOrigin() {
		return xIndex == 0 && yIndex == 0;
	}

	public int getWidthBlocks() {
		return width;
	}

	public int getHeightBlocks() {
		return height;
	}

	public int getXIndex() {
		return xIndex;
	}

	public int getYIndex() {
		return yIndex;
	}

	public int getScale() {
		return scale;
	}

	/** Direction in which xIndex grows: the viewer's right hand when facing the screen. */
	public Direction getRightDirection() {
		return getFacing().getCounterClockWise();
	}

	@Override
	public int pixelWidth() {
		return MonitorMath.pixelSize(width, pixelsPerBlock(), scale);
	}

	@Override
	public int pixelHeight() {
		return MonitorMath.pixelSize(height, pixelsPerBlock(), scale);
	}

	@Override
	protected int layoutXIndex() {
		return xIndex;
	}

	@Override
	protected int layoutYIndex() {
		return yIndex;
	}

	@Override
	protected int layoutWidth() {
		return width;
	}

	@Override
	protected int layoutHeight() {
		return height;
	}

	@Override
	public String getMonitorTypeName() {
		return "external_monitor";
	}

	@Override
	protected Object createMonitorObject() {
		return new ExternalMonitorLuaObject(this);
	}

	/**
	 * Changes the pixel size of the whole wall (1 = full resolution, 2 = half, ... {@link #MAX_SCALE}).
	 * On the server the change goes through the origin and is sent to clients; call on the server thread.
	 * @return true if the scale changed
	 */
	public boolean setScale(int newScale) {
		newScale = MonitorMath.clamp(newScale, 1, MAX_SCALE);
		if (level == null || level.isClientSide) {
			boolean changed = scale != newScale;
			scale = newScale;
			return changed;
		}
		if (isRemoved() || destroyed) return false;
		ExternalMonitorBlockEntity o = getNeighbour(0, 0);
		if (o == null) o = this;
		if (o.scale == newScale) return false;
		o.scale = newScale;
		o.propagateTerminal(new ArrayList<>());
		return true;
	}

	// ------------------------------------------------------------------ screen lookup

	@Nullable
	@Override
	public Monitor getMonitor() {
		if (isOrigin()) {
			if (mon == null) {
				mon = new Monitor(pixelWidth(), pixelHeight(), createMonitorObject());
				// Pixels loaded from NBT before the screen existed.
				byte[] png = pendingScreen;
				pendingScreen = null;
				if (png != null) decodeScreen(mon, png, false);
			}
			return mon;
		}
		ExternalMonitorBlockEntity o = getOrigin();
		return o == null || o == this ? null : o.getMonitor();
	}

	/** The origin block of this wall (this when it is the origin), or null when it is not loaded / not known yet. */
	@Nullable
	public ExternalMonitorBlockEntity getOrigin() {
		if (isOrigin()) return this;
		ExternalMonitorBlockEntity c = cachedOrigin;
		if (c != null && !c.isRemoved() && c.level == level && c.isOrigin() && c.width == width && c.height == height && c.getFacing() == getFacing()) {
			return c;
		}
		c = getNeighbour(0, 0);
		if (c != null && !c.isOrigin()) c = null;
		cachedOrigin = c;
		return c;
	}

	private void invalidateCaches() {
		cachedOrigin = null;
		renderBox = null;
	}

	/**
	 * Render bounds: the whole wall for the origin, the block itself otherwise. Overrides Forge's
	 * IForgeBlockEntity#getRenderBoundingBox at runtime; on Fabric the renderer can use it directly.
	 */
	public AABB getRenderBoundingBox() {
		AABB box = renderBox;
		if (box == null) {
			if (!isOrigin()) {
				box = new AABB(worldPosition);
			} else {
				BlockPos far = worldPosition.relative(getRightDirection(), width - 1).above(height - 1);
				box = new AABB(worldPosition).minmax(new AABB(far));
			}
			renderBox = box;
		}
		return box;
	}

	// ------------------------------------------------------------------ interaction

	/** Right click on the screen face: a click at the hit pixel on every GPU drawing to the wall. */
	@Override
	public InteractionResult onUse(Player player, InteractionHand hand, BlockHitResult hit) {
		if (hit.getDirection() != getFacing() || player.isShiftKeyDown()) return InteractionResult.PASS;
		if (level == null || level.isClientSide) return InteractionResult.SUCCESS;
		int[] px = hitToPixel(hit);
		Monitor m = getMonitor();
		if (px == null || m == null) return InteractionResult.SUCCESS;
		for (GPU g : new ArrayList<>(m.gpus)) {
			if (g.tile instanceof GpuBlockEntity gpu) {
				gpu.startClick(player, 0, px[0], px[1]);
				gpu.endClick(player);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void onNeighborChanged() {
		// The structure is maintained on placement and removal; nothing to do here.
	}

	// ------------------------------------------------------------------ persistence and sync

	private void writeLayout(CompoundTag tag) {
		tag.putInt("xIndex", xIndex);
		tag.putInt("yIndex", yIndex);
		tag.putInt("width", width);
		tag.putInt("height", height);
		tag.putInt("dir", getFacing().get2DDataValue());
		tag.putInt("scale", scale);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		writeLayout(tag);
	}

	@Override
	protected void writeClientData(CompoundTag tag) {
		super.writeClientData(tag);
		writeLayout(tag);
	}

	/** Only the origin carries the pixels; the other blocks share its screen. */
	@Override
	protected void writeScreen(CompoundTag tag, boolean forClient) {
		if (isOrigin()) super.writeScreen(tag, forClient);
	}

	@Override
	public void load(CompoundTag tag) {
		int w = tag.contains("width", Tag.TAG_INT) ? MonitorMath.clamp(tag.getInt("width"), 1, 64) : 1;
		int h = tag.contains("height", Tag.TAG_INT) ? MonitorMath.clamp(tag.getInt("height"), 1, 64) : 1;
		int xi = MonitorMath.clamp(tag.getInt("xIndex"), 0, w - 1);
		int yi = MonitorMath.clamp(tag.getInt("yIndex"), 0, h - 1);
		int sc = tag.contains("scale", Tag.TAG_INT) ? MonitorMath.clamp(tag.getInt("scale"), 1, MAX_SCALE) : 1;
		boolean changed = w != width || h != height || xi != xIndex || yi != yIndex || sc != scale;
		width = w;
		height = h;
		xIndex = xi;
		yIndex = yi;
		scale = sc;
		if (changed) invalidateCaches();
		if (!isOrigin()) {
			// This block is not (or no longer) the origin: its screen lives on the origin.
			mon = null;
			pendingScreen = null;
		} else if (mon != null) {
			MonitorLocks.resize(mon, pixelWidth(), pixelHeight(), true);
		}
		super.load(tag);
	}

	@Override
	protected void readScreen(CompoundTag tag) {
		if (!isOrigin() || !tag.contains(TAG_SCREEN, Tag.TAG_BYTE_ARRAY)) return;
		byte[] png = tag.getByteArray(TAG_SCREEN);
		if (mon != null) {
			// The wall's size decides the screen size; draw the saved pixels into it as they fit.
			decodeScreen(mon, png, false);
		} else {
			pendingScreen = png;
		}
	}

	// ------------------------------------------------------------------ merging (server)

	/** Called by the block when it is removed (server): splits the wall around this block. */
	public void destroy() {
		if (destroyed) return;
		destroyed = true;
		if (level != null && !level.isClientSide) contractNeighbours();
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	/** Called by the block after placement on the server: merges with neighbouring walls. */
	public void onPlaced() {
		if (level == null || level.isClientSide) return;
		contractNeighbours();
		contract();
		expand();
		dirty = true;
	}

	@Nullable
	private ExternalMonitorBlockEntity getSimilarMonitorAt(BlockPos pos) {
		if (level == null || !level.isLoaded(pos)) return null;
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ExternalMonitorBlockEntity m)) return null;
		if (m.isRemoved() || m.destroyed || m.ignoreMe || m.getFacing() != getFacing()) return null;
		return m;
	}

	/** The block at wall position (x, y) of this block's wall, or null when missing. */
	@Nullable
	public ExternalMonitorBlockEntity getNeighbour(int x, int y) {
		return getSimilarMonitorAt(worldPosition.relative(getRightDirection(), x - xIndex).above(y - yIndex));
	}

	/**
	 * Rebuilds the shared screen of this wall (this block must be its origin), keeping the old pixels
	 * where possible, and moves the GPUs of {@code oldScreens} (screens of blocks that stopped being an
	 * origin) onto it so running programs keep drawing.
	 */
	private void propagateTerminal(List<Monitor> oldScreens) {
		ExternalMonitorBlockEntity origin = getNeighbour(0, 0);
		if (origin == null) return;
		Monitor shared = origin.getMonitor();
		if (shared == null) return;
		if (!(shared.obj instanceof MonitorLuaObject o) || o.owner() != origin) shared.obj = origin.createMonitorObject();
		MonitorLocks.resize(shared, origin.pixelWidth(), origin.pixelHeight(), true);
		for (Monitor old : oldScreens) MonitorLocks.migrateGpus(old, shared);
		for (int y = 0; y < origin.height; y++) {
			for (int x = 0; x < origin.width; x++) {
				ExternalMonitorBlockEntity tile = origin.getNeighbour(x, y);
				if (tile == null) continue;
				tile.scale = origin.scale;
				tile.dirty = true;
				tile.renderBox = null;
				if (tile != origin && tile.mon != null) {
					MonitorLocks.migrateGpus(tile.mon, shared);
					tile.mon = null;
				}
			}
		}
		origin.dirty = true;
	}

	/** Makes this block the origin of a width x height wall extending right and up from it. */
	private void resize(int w, int h) {
		Direction right = getRightDirection();
		List<Monitor> oldScreens = new ArrayList<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				ExternalMonitorBlockEntity m = getSimilarMonitorAt(worldPosition.relative(right, x).above(y));
				if (m == null) continue;
				m.xIndex = x;
				m.yIndex = y;
				m.width = w;
				m.height = h;
				m.invalidateCaches();
				m.dirty = true;
				if (m != this && m.mon != null) {
					oldScreens.add(m.mon);
					m.mon = null;
					m.pendingScreen = null;
				}
			}
		}
		propagateTerminal(oldScreens);
	}

	private boolean mergeLeft() {
		ExternalMonitorBlockEntity left = getNeighbour(-1, 0);
		if (left != null && left.yIndex == 0 && left.height == height) {
			int w = left.width + width;
			if (w <= Config.externalMonitorMaxWidth) {
				ExternalMonitorBlockEntity o = left.getNeighbour(0, 0);
				if (o != null) {
					o.resize(w, height);
					left.expand();
					return true;
				}
			}
		}
		return false;
	}

	private boolean mergeRight() {
		ExternalMonitorBlockEntity right = getNeighbour(width, 0);
		if (right != null && right.yIndex == 0 && right.height == height) {
			int w = width + right.width;
			if (w <= Config.externalMonitorMaxWidth) {
				ExternalMonitorBlockEntity o = getNeighbour(0, 0);
				if (o != null) {
					o.resize(w, height);
					expand();
					return true;
				}
			}
		}
		return false;
	}

	private boolean mergeUp() {
		ExternalMonitorBlockEntity above = getNeighbour(0, height);
		if (above != null && above.xIndex == 0 && above.width == width) {
			int h = above.height + height;
			if (h <= Config.externalMonitorMaxHeight) {
				ExternalMonitorBlockEntity o = getNeighbour(0, 0);
				if (o != null) {
					o.resize(width, h);
					expand();
					return true;
				}
			}
		}
		return false;
	}

	private boolean mergeDown() {
		ExternalMonitorBlockEntity below = getNeighbour(0, -1);
		if (below != null && below.xIndex == 0 && below.width == width) {
			int h = height + below.height;
			if (h <= Config.externalMonitorMaxHeight) {
				ExternalMonitorBlockEntity o = below.getNeighbour(0, 0);
				if (o != null) {
					o.resize(width, h);
					below.expand();
					return true;
				}
			}
		}
		return false;
	}

	/** Merges with neighbouring walls of the same facing while the size limits allow. */
	public void expand() {
		dirty = true;
		int guard = 0;
		while ((mergeLeft() || mergeRight() || mergeUp() || mergeDown()) && ++guard < MAX_MERGE_STEPS) {}
	}

	/** Called on the block being removed: the rest of the wall splits into complete rectangles. */
	public void contractNeighbours() {
		if (mon != null) MonitorLocks.detachAll(mon);
		ignoreMe = true;
		try {
			if (xIndex > 0) {
				ExternalMonitorBlockEntity left = getNeighbour(xIndex - 1, yIndex);
				if (left != null) left.contract();
			}
			if (xIndex + 1 < width) {
				ExternalMonitorBlockEntity right = getNeighbour(xIndex + 1, yIndex);
				if (right != null) right.contract();
			}
			if (yIndex > 0) {
				ExternalMonitorBlockEntity below = getNeighbour(xIndex, yIndex - 1);
				if (below != null) below.contract();
			}
			if (yIndex + 1 < height) {
				ExternalMonitorBlockEntity above = getNeighbour(xIndex, yIndex + 1);
				if (above != null) above.contract();
			}
		} finally {
			ignoreMe = false;
		}
	}

	/** Splits this wall into the largest rectangles that are still complete. */
	public void contract() {
		dirty = true;
		int h = height;
		int w = width;
		ExternalMonitorBlockEntity origin = getNeighbour(0, 0);
		if (origin == null) {
			// The origin is gone: row 0 right of it becomes one wall, the rows above another.
			ExternalMonitorBlockEntity rest = w > 1 ? getNeighbour(1, 0) : null;
			ExternalMonitorBlockEntity upper = h > 1 ? getNeighbour(0, 1) : null;
			if (rest != null) rest.resize(w - 1, 1);
			if (upper != null) upper.resize(w, h - 1);
			if (rest != null) rest.expand();
			if (upper != null) upper.expand();
			if (rest != this && upper != this) {
				// This block was in neither piece (its indices were stale): stand alone.
				if (mon != null) MonitorLocks.detachAll(mon);
				mon = null;
				width = height = 1;
				xIndex = yIndex = 0;
				invalidateCaches();
				propagateTerminal(new ArrayList<>());
				expand();
			}
			return;
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (origin.getNeighbour(x, y) != null) continue;
				// (x,y) is the hole: split into the rows below it, the parts left/right of it and the rows above.
				ExternalMonitorBlockEntity lower = null, left = null, right = null, upper = null;
				if (y > 0) {
					lower = origin;
					lower.resize(w, y);
				}
				if (x > 0) {
					left = origin.getNeighbour(0, y);
					if (left != null) left.resize(x, 1);
				}
				if (x + 1 < w) {
					right = origin.getNeighbour(x + 1, y);
					if (right != null) right.resize(w - (x + 1), 1);
				}
				if (y + 1 < h) {
					upper = origin.getNeighbour(0, y + 1);
					if (upper != null) upper.resize(w, h - (y + 1));
				}
				if (lower != null) lower.expand();
				if (left != null) left.expand();
				if (right != null) right.expand();
				if (upper != null) upper.expand();
				return;
			}
		}
	}
}
