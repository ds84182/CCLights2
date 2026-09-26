package ds.mods.CCLights2.block.tileentity;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.gpu.Monitor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;

/**
 * One block of a multi-block external monitor. Adjacent blocks facing the same way merge into a
 * rectangle; the block at index (0,0) (the origin) owns the shared {@link Monitor} and renders it.
 * <p>
 * Merging runs on the server only. Clients receive width/height/index/direction through the
 * description packet and look up the origin lazily, caching the reference so rendering never
 * has to walk the world.
 */
public class TileEntityExternalMonitor extends TileEntityMonitor {
	/** Screen pixels per block before scaling; taken from the config. */
	public static int pixelsPerBlock() {
		return Config.externalMonitorPixelsPerBlock;
	}
	public static final int MAX_SCALE = 8;

	public int m_width = 1;
	public int m_height = 1;
	public int m_xIndex = 0;
	public int m_yIndex = 0;
	public int m_dir = 2;
	/** Pixel magnification: the screen has pixelsPerBlock() / m_scale pixels per block. */
	public int m_scale = 1;
	private boolean destroyed = false;
	private boolean ignoreMe = false;
	private boolean dirty = false;

	// client-side caches
	private TileEntityExternalMonitor cachedOrigin;
	private AxisAlignedBB renderBox;
	/** Screen pixels read from disk before the tile had a world. */
	private byte[] pendingScreen;

	public TileEntityExternalMonitor() {
		super(false);
	}

	// ------------------------------------------------------------------ geometry

	public int getDir() {
		return m_dir;
	}

	public void setDir(int dir) {
		m_dir = dir & 3;
		dirty = true;
	}

	/** Side index (Facing.*) along which xIndex increases. */
	public int getRight() {
		switch (m_dir) {
		case 0: return 5;
		case 1: return 2;
		case 2: return 4;
		default: return 3;
		}
	}

	/** Side index (Facing.*) the screen faces. */
	public int getFront() {
		switch (m_dir) {
		case 0: return 2;
		case 1: return 5;
		case 2: return 3;
		default: return 4;
		}
	}

	/**
	 * True when xIndex grows towards the viewer's right hand when looking at the screen.
	 * The merge algorithm's "right" is only the viewer's right for two of the four directions.
	 */
	public boolean indexGrowsToViewerRight() {
		return m_dir == 1 || m_dir == 3;
	}

	public boolean isOrigin() {
		return m_xIndex == 0 && m_yIndex == 0;
	}

	public int getScale() {
		return m_scale;
	}

	/** Screen width in pixels for the current size and scale. */
	public int pixelWidth() {
		return Math.max(1, m_width * pixelsPerBlock() / m_scale);
	}

	public int pixelHeight() {
		return Math.max(1, m_height * pixelsPerBlock() / m_scale);
	}

	/** Server only: changes the pixel size of the whole multi-block (1 = 32 px per block, 2 = 16, ...). */
	public boolean setScale(int scale) {
		scale = Math.max(1, Math.min(MAX_SCALE, scale));
		TileEntityExternalMonitor o = worldObj == null || worldObj.isRemote ? this : getNeighbour(0, 0);
		if (o == null) o = this;
		if (o.m_scale == scale) return false;
		o.m_scale = scale;
		if (worldObj != null && !worldObj.isRemote) o.propagateTerminal();
		return true;
	}

	public int getWidth() {
		return m_width;
	}

	public int getHeight() {
		return m_height;
	}

	public int getXIndex() {
		return m_xIndex;
	}

	public int getYIndex() {
		return m_yIndex;
	}

	/**
	 * Converts a hit on the screen face to screen pixel coordinates.
	 * @param along fraction 0..1 of the hit along the viewer's right axis within this block
	 * @param up fraction 0..1 of the hit from the block's bottom
	 */
	public int[] hitToPixel(float along, float up) {
		int column = indexGrowsToViewerRight() ? m_xIndex : (m_width - 1 - m_xIndex);
		int px = (int) Math.floor((column + along) * pixelsPerBlock() / m_scale);
		int py = (int) Math.floor(((m_height - 1 - m_yIndex) + (1f - up)) * pixelsPerBlock() / m_scale);
		return new int[] { px, py };
	}

	// ------------------------------------------------------------------ monitor lookup

	@Override
	public Monitor getMonitor() {
		if (mon != null) return mon;
		if (worldObj == null) return null;
		if (isOrigin()) {
			mon = new Monitor(pixelWidth(), pixelHeight(), getMonitorObject());
			return mon;
		}
		// Non-origin blocks share the origin's screen; never cache it here so structure changes are picked up.
		TileEntityExternalMonitor o = origin();
		return o == null || o == this ? null : o.getMonitor();
	}

	/** Origin tile of this multi-block, cached on the client. */
	public TileEntityExternalMonitor origin() {
		if (isOrigin()) return this;
		if (cachedOrigin != null && !cachedOrigin.isInvalid() && cachedOrigin.isOrigin()
				&& cachedOrigin.m_width == m_width && cachedOrigin.m_height == m_height && cachedOrigin.m_dir == m_dir) {
			return cachedOrigin;
		}
		cachedOrigin = getNeighbour(0, 0);
		return cachedOrigin;
	}

	private void invalidateCaches() {
		cachedOrigin = null;
		renderBox = null;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if (renderBox == null) {
			if (!isOrigin()) {
				renderBox = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1);
			} else {
				int right = getRight();
				int rx = Facing.offsetsXForSide[right] * (m_width - 1);
				int rz = Facing.offsetsZForSide[right] * (m_width - 1);
				renderBox = AxisAlignedBB.getBoundingBox(
						Math.min(xCoord, xCoord + rx), yCoord, Math.min(zCoord, zCoord + rz),
						Math.max(xCoord, xCoord + rx) + 1, yCoord + m_height, Math.max(zCoord, zCoord + rz) + 1);
			}
		}
		return renderBox;
	}

	// ------------------------------------------------------------------ sync and persistence

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("xIndex", m_xIndex);
		nbt.setInteger("yIndex", m_yIndex);
		nbt.setInteger("width", m_width);
		nbt.setInteger("height", m_height);
		nbt.setInteger("dir", m_dir);
		nbt.setInteger("scale", m_scale);
	}

	@Override
	protected void writeScreen(NBTTagCompound nbt) {
		// Only the origin carries the pixels; the others share its Monitor.
		if (isOrigin()) super.writeScreen(nbt);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		int w = Math.max(1, nbt.getInteger("width"));
		int h = Math.max(1, nbt.getInteger("height"));
		int xi = nbt.getInteger("xIndex");
		int yi = nbt.getInteger("yIndex");
		int dir = nbt.getInteger("dir") & 3;
		int scale = nbt.hasKey("scale") ? Math.max(1, Math.min(MAX_SCALE, nbt.getInteger("scale"))) : 1;
		boolean changed = w != m_width || h != m_height || xi != m_xIndex || yi != m_yIndex || dir != m_dir || scale != m_scale;
		m_width = w;
		m_height = h;
		m_xIndex = xi;
		m_yIndex = yi;
		m_dir = dir;
		m_scale = scale;
		if (changed) invalidateCaches();
		if (mon != null && !isOrigin()) {
			// This block stopped being the origin: its screen now lives on the origin.
			mon = null;
		}
		if (mon != null) mon.resize(pixelWidth(), pixelHeight(), true);
		super.readFromNBT(nbt);
	}

	@Override
	protected void readScreen(NBTTagCompound nbt) {
		if (!isOrigin()) return;
		if (worldObj == null) {
			// Loading from disk: the screen is created once the tile is in a world (see validate()).
			pendingScreen = nbt.hasKey("screen") ? nbt.getByteArray("screen") : null;
			return;
		}
		getMonitor();
		super.readScreen(nbt);
	}

	@Override
	public void validate() {
		super.validate();
		if (pendingScreen != null && isOrigin()) {
			getMonitor();
			decodeScreen(pendingScreen);
		}
		pendingScreen = null;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {
		if (worldObj.isRemote) return;
		if (dirty) {
			dirty = false;
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	// ------------------------------------------------------------------ merging (server)

	public void destroy() {
		if (destroyed) return;
		destroyed = true;
		if (!worldObj.isRemote) contractNeighbours();
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	/** Called by the block after placement on the server. */
	public void onPlaced(int dir) {
		setDir(dir);
		contractNeighbours();
		contract();
		expand();
	}

	private TileEntityExternalMonitor getSimilarMonitorAt(int x, int y, int z) {
		if (y < 0 || y >= worldObj.getHeight()) return null;
		if (!worldObj.getChunkProvider().chunkExists(x >> 4, z >> 4)) return null;
		TileEntity tile = worldObj.getTileEntity(x, y, z);
		if (!(tile instanceof TileEntityExternalMonitor)) return null;
		TileEntityExternalMonitor m = (TileEntityExternalMonitor) tile;
		if (m.m_dir != m_dir || m.destroyed || m.ignoreMe) return null;
		return m;
	}

	public TileEntityExternalMonitor getNeighbour(int x, int y) {
		int right = getRight();
		int xOffset = -m_xIndex + x;
		return getSimilarMonitorAt(xCoord + Facing.offsetsXForSide[right] * xOffset, yCoord - m_yIndex + y, zCoord + Facing.offsetsZForSide[right] * xOffset);
	}

	/** Rebuilds the shared screen of this multi-block, keeping the old pixels where possible. */
	private void propagateTerminal() {
		TileEntityExternalMonitor origin = getNeighbour(0, 0);
		if (origin == null) return;
		int w = origin.pixelWidth(), h = origin.pixelHeight();
		Monitor shared = origin.mon;
		if (shared == null) {
			shared = new Monitor(w, h, origin.getMonitorObject());
			origin.mon = shared;
		} else {
			// Same Monitor object, new size: attached GPUs keep drawing to it.
			shared.resize(w, h, true);
		}
		for (int y = 0; y < m_height; y++) {
			for (int x = 0; x < m_width; x++) {
				TileEntityExternalMonitor tile = getNeighbour(x, y);
				if (tile == null) continue;
				tile.m_scale = origin.m_scale;
				tile.dirty = true;
				if (tile == origin) continue;
				if (tile.mon != null && tile.mon != shared) {
					// Move GPUs from the block's private screen to the shared one so programs keep running.
					for (ds.mods.CCLights2.gpu.GPU g : new java.util.ArrayList<ds.mods.CCLights2.gpu.GPU>(tile.mon.gpus)) {
						shared.addGPU(g);
						tile.mon.removeGPU(g);
					}
				}
				tile.mon = shared;
			}
		}
		origin.dirty = true;
	}

	public void resize(int width, int height) {
		int right = getRight();
		int rightX = Facing.offsetsXForSide[right];
		int rightZ = Facing.offsetsZForSide[right];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				TileEntityExternalMonitor m = getSimilarMonitorAt(xCoord + rightX * x, yCoord + y, zCoord + rightZ * x);
				if (m == null) continue;
				m.m_xIndex = x;
				m.m_yIndex = y;
				m.m_width = width;
				m.m_height = height;
				m.invalidateCaches();
				m.dirty = true;
			}
		}
		propagateTerminal();
	}

	private boolean mergeLeft() {
		TileEntityExternalMonitor left = getNeighbour(-1, 0);
		if (left != null && left.m_yIndex == 0 && left.m_height == m_height) {
			int width = left.m_width + m_width;
			if (width <= Config.externalMonitorMaxWidth) {
				TileEntityExternalMonitor o = left.getNeighbour(0, 0);
				if (o != null) {
					o.resize(width, m_height);
					left.expand();
					return true;
				}
			}
		}
		return false;
	}

	private boolean mergeRight() {
		TileEntityExternalMonitor right = getNeighbour(m_width, 0);
		if (right != null && right.m_yIndex == 0 && right.m_height == m_height) {
			int width = m_width + right.m_width;
			if (width <= Config.externalMonitorMaxWidth) {
				TileEntityExternalMonitor o = getNeighbour(0, 0);
				if (o != null) {
					o.resize(width, m_height);
					expand();
					return true;
				}
			}
		}
		return false;
	}

	private boolean mergeUp() {
		TileEntityExternalMonitor above = getNeighbour(0, m_height);
		if (above != null && above.m_xIndex == 0 && above.m_width == m_width) {
			int height = above.m_height + m_height;
			if (height <= Config.externalMonitorMaxHeight) {
				TileEntityExternalMonitor o = getNeighbour(0, 0);
				if (o != null) {
					o.resize(m_width, height);
					expand();
					return true;
				}
			}
		}
		return false;
	}

	private boolean mergeDown() {
		TileEntityExternalMonitor below = getNeighbour(0, -1);
		if (below != null && below.m_xIndex == 0 && below.m_width == m_width) {
			int height = m_height + below.m_height;
			if (height <= Config.externalMonitorMaxHeight) {
				TileEntityExternalMonitor o = below.getNeighbour(0, 0);
				if (o != null) {
					o.resize(m_width, height);
					below.expand();
					return true;
				}
			}
		}
		return false;
	}

	public void expand() {
		dirty = true;
		int guard = 0;
		while ((mergeLeft() || mergeRight() || mergeUp() || mergeDown()) && ++guard < 64) {}
	}

	public void contractNeighbours() {
		if (mon != null) mon.removeAllGPUs();
		ignoreMe = true;
		if (m_xIndex > 0) {
			TileEntityExternalMonitor left = getNeighbour(m_xIndex - 1, m_yIndex);
			if (left != null) left.contract();
		}
		if (m_xIndex + 1 < m_width) {
			TileEntityExternalMonitor right = getNeighbour(m_xIndex + 1, m_yIndex);
			if (right != null) right.contract();
		}
		if (m_yIndex > 0) {
			TileEntityExternalMonitor below = getNeighbour(m_xIndex, m_yIndex - 1);
			if (below != null) below.contract();
		}
		if (m_yIndex + 1 < m_height) {
			TileEntityExternalMonitor above = getNeighbour(m_xIndex, m_yIndex + 1);
			if (above != null) above.contract();
		}
		ignoreMe = false;
	}

	/** Splits this multi-block into the largest rectangles that are still complete. */
	public void contract() {
		dirty = true;
		int height = m_height;
		int width = m_width;
		TileEntityExternalMonitor origin = getNeighbour(0, 0);
		if (origin == null) {
			// The origin is gone: everything to the right and above becomes its own rectangle.
			TileEntityExternalMonitor right = width > 1 ? getNeighbour(1, 0) : null;
			TileEntityExternalMonitor below = height > 1 ? getNeighbour(0, 1) : null;
			if (right != null) right.resize(width - 1, 1);
			if (below != null) below.resize(width, height - 1);
			if (right != null) right.expand();
			if (below != null) below.expand();
			if (mon != null) mon.removeAllGPUs();
			mon = null;
			m_width = m_height = 1;
			m_xIndex = m_yIndex = 0;
			invalidateCaches();
			return;
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				TileEntityExternalMonitor m = origin.getNeighbour(x, y);
				if (m != null) continue;
				// (x,y) is the hole: split into the rows below it, the parts left/right of it, and the rows above.
				TileEntityExternalMonitor above = null, left = null, right = null, below = null;
				if (y > 0) {
					above = origin;
					above.resize(width, y);
				}
				if (x > 0) {
					left = origin.getNeighbour(0, y);
					if (left != null) left.resize(x, 1);
				}
				if (x + 1 < width) {
					right = origin.getNeighbour(x + 1, y);
					if (right != null) right.resize(width - (x + 1), 1);
				}
				if (y + 1 < height) {
					below = origin.getNeighbour(0, y + 1);
					if (below != null) below.resize(width, height - (y + 1));
				}
				if (above != null) above.expand();
				if (left != null) left.expand();
				if (right != null) right.expand();
				if (below != null) below.expand();
				return;
			}
		}
	}

	// ------------------------------------------------------------------ lua

	@Override
	public ILuaObject getMonitorObject() {
		return new ExternalMonitorObject();
	}

	@Override
	protected String monitorTypeName() {
		return "external_monitor";
	}

	public class ExternalMonitorObject extends MonitorObject {
		@Override
		public String[] getMethodNames() {
			return new String[] { "getResolution", "getSize", "getType", "getPosition", "getDPM", "getBlockResolution", "setScale", "getScale" };
		}

		@Override
		public Object[] callMethod(ILuaContext context, int method, Object[] arguments) throws LuaException {
			switch (method) {
			case 4:
				return new Object[] { pixelsPerBlock() / m_scale };
			case 5:
				return new Object[] { m_width, m_height };
			case 6: {
				int scale = ds.mods.CCLights2.gpu.LuaArgs.getInt(arguments, 0, "setScale");
				if (scale < 1 || scale > MAX_SCALE) throw new LuaException("setScale: scale must be between 1 and " + MAX_SCALE);
				return new Object[] { setScale(scale) };
			}
			case 7:
				return new Object[] { m_scale };
			default:
				return super.callMethod(context, method, arguments);
			}
		}
	}
}
