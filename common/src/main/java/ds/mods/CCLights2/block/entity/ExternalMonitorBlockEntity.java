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
import ds.mods.CCLights2.utils.WallLayout;
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
public class ExternalMonitorBlockEntity extends MonitorBlockEntity implements WallLayout.Cell {
	public static final int MAX_SCALE = 8;

	private int width = 1;
	private int height = 1;
	private int xIndex = 0;
	private int yIndex = 0;
	/** Pixel magnification: the screen has pixelsPerBlock() / scale pixels per block. */
	private int scale = 1;
	private boolean destroyed = false;
	private boolean dirty = false;
	/** Server: the layout was checked against the neighbours once after loading. */
	private boolean layoutValidated = false;

	/**
	 * Server thread only: wall blocks whose layout changed in the current merge/split, flushed to the
	 * clients as soon as the operation ends. Waiting for each block's own tick is not enough: blocks in
	 * loaded but non-ticking chunks (outside the simulation distance) would never send their update and
	 * the clients would keep drawing the old wall over the new one.
	 */
	private static final java.util.LinkedHashSet<ExternalMonitorBlockEntity> PENDING_UPDATES = new java.util.LinkedHashSet<>();

	/** Screen of a block that just stopped being an origin; its GPUs move to the wall it now belongs to. */
	@Nullable
	private Monitor orphanScreen;

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
	/** How often (ticks) a block re-checks that its wall is intact; catches chunks that came back without a reload. */
	private static final int LAYOUT_CHECK_TICKS = 60;
	private int checkTicks = 0;

	public static void serverTick(Level level, BlockPos pos, BlockState state, ExternalMonitorBlockEntity be) {
		if (!be.layoutValidated) be.validateLayout();
		else if (++be.checkTicks >= LAYOUT_CHECK_TICKS) {
			be.checkTicks = 0;
			if (!be.layoutIntact()) be.validateLayout();
		}
		if (be.dirty) {
			be.dirty = false;
			be.sendUpdate();
		}
	}

	/** Marks this block's layout as changed; flushed by {@link #flushPendingUpdates()} or the next tick. */
	private void touch() {
		dirty = true;
		if (level != null && !level.isClientSide) PENDING_UPDATES.add(this);
	}

	/** Sends the update tag of every block touched by the current merge/split to the clients now. */
	private static void flushPendingUpdates() {
		if (PENDING_UPDATES.isEmpty()) return;
		List<ExternalMonitorBlockEntity> tiles = new ArrayList<>(PENDING_UPDATES);
		PENDING_UPDATES.clear();
		for (ExternalMonitorBlockEntity t : tiles) {
			if (t.isRemoved() || t.level == null) continue;
			t.dirty = false;
			t.sendUpdate();
		}
	}

	/**
	 * Server, once after loading and whenever a neighbour changed: rebuilds the walls around this block
	 * from the blocks actually present, so stale indices (neighbours changed while this chunk was
	 * unloaded) and blocks placed by commands are picked up. Waits until the neighbourhood is loaded.
	 */
	private void validateLayout() {
		if (level == null || level.isClientSide || destroyed || isRemoved()) return;
		for (Direction d : Direction.values()) if (!level.isLoaded(worldPosition.relative(d))) return;
		layoutValidated = true;
		relayout();
	}

	/**
	 * Cheap check that this block's wall still exists as stored: the origin is there and claims this
	 * block (for members), or every cell is present with matching indices (for origins). Cells in
	 * unloaded chunks count as fine until they load.
	 */
	private boolean layoutIntact() {
		if (level == null) return true;
		if (!isOrigin()) {
			BlockPos op = worldPosition.relative(getRightDirection(), -xIndex).below(yIndex);
			if (!level.isLoaded(op)) return true;
			ExternalMonitorBlockEntity o = getSimilarMonitorAt(op);
			return o != null && o.isOrigin() && o.width == width && o.height == height;
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlockPos p = worldPosition.relative(getRightDirection(), x).above(y);
				if (!level.isLoaded(p)) continue;
				ExternalMonitorBlockEntity m = getSimilarMonitorAt(p);
				if (m == null || m.xIndex != x || m.yIndex != y || m.width != width || m.height != height) return false;
			}
		}
		return true;
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		// a block (re)added to the level is checked again against its (possibly changed) neighbours
		layoutValidated = false;
		validationAttempts = 0;
		scheduleValidation();
	}

	/** Cells of a wall that were in unloaded chunks during the last relayout; validated when they load. */
	@Nullable
	private List<BlockPos> unloadedCells;
	private int unloadedChecks;
	private static final int UNLOADED_CHECK_TICKS = 20;
	private static final int UNLOADED_CHECK_LIMIT = 3000; // about 17 minutes

	/**
	 * Part of a wall was in unloaded space when the wall around this block changed, so those blocks
	 * still carry the old layout. A chunk that is only demoted and later promoted again gets its block
	 * entities back without any reload hook and may never tick (view distance ring), so the loaded side
	 * polls the server task queue until those positions are loaded and then validates them directly.
	 */
	private void watchUnloaded(List<BlockPos> cells) {
		unloadedCells = cells;
		unloadedChecks = 0;
		scheduleUnloadedCheck();
	}

	private void scheduleUnloadedCheck() {
		if (level == null || level.isClientSide) return;
		net.minecraft.server.MinecraftServer server = level.getServer();
		if (server == null) return;
		server.tell(new net.minecraft.server.TickTask(server.getTickCount() + UNLOADED_CHECK_TICKS, () -> {
			List<BlockPos> cells = unloadedCells;
			if (cells == null || isRemoved() || destroyed || level == null) return;
			for (BlockPos p : cells) {
				if (!level.isLoaded(p)) {
					if (++unloadedChecks < UNLOADED_CHECK_LIMIT) scheduleUnloadedCheck();
					else unloadedCells = null;
					return;
				}
			}
			unloadedCells = null;
			for (BlockPos p : cells) {
				if (level.getBlockEntity(p) instanceof ExternalMonitorBlockEntity m && !m.destroyed) {
					m.layoutValidated = false;
					m.validateLayout();
				}
			}
			layoutValidated = false;
			validateLayout();
		}));
	}

	/** Bounded retries (ticks) for the validation scheduled after a chunk load, while neighbours load. */
	private static final int VALIDATION_ATTEMPTS = 200;
	private int validationAttempts;

	/**
	 * Chunks inside the view distance but outside the simulation distance are loaded but never tick, so
	 * the ticker cannot run {@link #validateLayout()} there; a wall broken on the other side of a chunk
	 * border while this side was unloaded would keep its stale layout (and draw over air) until a player
	 * came close. Validation is therefore also run from the server's task queue after a (re)load, retried
	 * a few ticks apart until the neighbourhood is loaded.
	 */
	private void scheduleValidation() {
		if (level == null || level.isClientSide) return;
		net.minecraft.server.MinecraftServer server = level.getServer();
		if (server == null) return;
		server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 2, () -> {
			if (isRemoved() || destroyed || layoutValidated || level == null) return;
			validateLayout();
			if (!layoutValidated && ++validationAttempts < VALIDATION_ATTEMPTS) scheduleValidation();
		}));
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
		flushPendingUpdates();
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
		// Merging happens on placement/removal and on the first tick; a standalone block re-checks its
		// neighbours when they change so command-placed neighbours are picked up too.
		if (level != null && !level.isClientSide) layoutValidated = false;
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
		// Client: GPUs next to this block must re-attach right away (their screen object may have
		// changed), not at their next periodic rescan, or their draw lists are dropped meanwhile.
		if (changed && level != null && level.isClientSide) {
			for (Direction d : Direction.values()) {
				if (level.getBlockEntity(worldPosition.relative(d)) instanceof GpuBlockEntity g) g.onNeighborChanged();
			}
		}
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
		if (level == null || level.isClientSide) return;
		if (mon != null) MonitorLocks.detachAll(mon);
		// this block is already invisible to getSimilarMonitorAt (destroyed), so it is the hole
		relayout();
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	/** Called by the block after placement on the server: merges with neighbouring walls. */
	public void onPlaced() {
		if (level == null || level.isClientSide) return;
		relayout();
	}

	// ------------------------------------------------------------------ WallLayout.Cell

	@Override
	public int xIndex() {
		return xIndex;
	}

	@Override
	public int yIndex() {
		return yIndex;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public void setLayout(int xi, int yi, int w, int h) {
		xIndex = xi;
		yIndex = yi;
		width = w;
		height = h;
		invalidateCaches();
		touch();
		if (!isOrigin()) {
			if (mon != null) {
				// stopped being an origin: the wall this block joins inherits the screen's GPUs
				orphanScreen = mon;
				mon = null;
			}
			pendingScreen = null;
		}
	}

	/**
	 * Server: rebuilds the walls around this block from the blocks actually present (see
	 * {@link WallLayout}), then rebuilds the shared screens of the walls that changed and pushes the new
	 * layout to the clients right away.
	 */
	private void relayout() {
		if (level == null || level.isClientSide) return;
		final Direction right = getRightDirection();
		final BlockPos base = worldPosition;
		WallLayout.Grid grid = new WallLayout.Grid() {
			@Override
			public WallLayout.Cell at(int gx, int gy) {
				return getSimilarMonitorAt(base.relative(right, gx).above(gy));
			}

			@Override
			public boolean isLoaded(int gx, int gy) {
				return level.isLoaded(base.relative(right, gx).above(gy));
			}

			@Override
			public int maxWidth() {
				return Config.externalMonitorMaxWidth;
			}

			@Override
			public int maxHeight() {
				return Config.externalMonitorMaxHeight;
			}
		};
		WallLayout.Result result = WallLayout.relayout(grid, 0, 0);
		if (!result.unloaded.isEmpty()) {
			List<BlockPos> cells = new ArrayList<>();
			for (int[] c : result.unloaded) cells.add(base.relative(right, c[0]).above(c[1]));
			watchUnloaded(cells);
		}
		for (WallLayout.Placed p : result.origins) {
			ExternalMonitorBlockEntity origin = (ExternalMonitorBlockEntity) p.cell();
			List<Monitor> screens = new ArrayList<>();
			boolean changed = false;
			for (int y = 0; y < origin.height; y++) {
				for (int x = 0; x < origin.width; x++) {
					ExternalMonitorBlockEntity m = getSimilarMonitorAt(base.relative(right, p.gx() + x).above(p.gy() + y));
					if (m == null) continue;
					if (result.changed.contains(m)) changed = true;
					if (m.orphanScreen != null) {
						screens.add(m.orphanScreen);
						m.orphanScreen = null;
					}
				}
			}
			if (changed || !screens.isEmpty()) origin.propagateTerminal(screens);
		}
		flushPendingUpdates();
	}

	@Nullable
	private ExternalMonitorBlockEntity getSimilarMonitorAt(BlockPos pos) {
		if (level == null || !level.isLoaded(pos)) return null;
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ExternalMonitorBlockEntity m)) return null;
		if (m.isRemoved() || m.destroyed || m.getFacing() != getFacing()) return null;
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
				tile.touch();
				tile.renderBox = null;
				if (tile != origin && tile.mon != null) {
					MonitorLocks.migrateGpus(tile.mon, shared);
					tile.mon = null;
				}
			}
		}
		origin.touch();
	}
}
