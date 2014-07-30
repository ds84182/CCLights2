package ds.mods.CCLights2.block.tileentity;

import java.awt.Color;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.google.common.io.ByteArrayDataInput;

import cpw.mods.fml.common.FMLLog;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.network.PacketSenders;

public class TileEntityExternalMonitor extends TileEntityMonitor implements IPeripheral {
	public static final int MAX_WIDTH = 16;
	public static final int MAX_HEIGHT = 9;
	public static final int TICKS_TIL_SYNC = 20 * 600;
	public boolean dirty = false;
	public boolean m_destroyed = false;
	public boolean m_ignoreMe = false;
	public int m_connections = 0;
	public int m_totalConnections = 0;
	public int m_width = 1;
	public int m_height = 1;
	public int m_xIndex = 0;
	public int m_yIndex = 0;
	public int m_dir = 2;
	public int m_tts = TICKS_TIL_SYNC;
	public Monitor m_originMonitor;

	public TileEntityExternalMonitor() {
		mon = new Monitor(32, 32,getMonitorObject());
		mon.tex.fill(Color.black);
	}

	public void destroy() {
		if (!this.m_destroyed) {
			this.m_destroyed = true;
			if (!this.worldObj.isRemote) {
				contractNeighbours();
			}
		}
	}

	public boolean isDestroyed() {
		return this.m_destroyed;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if ((getXIndex() != 0) || (getYIndex() != 0)) {
			return CCLights2.monitorBig.getCollisionBoundingBoxFromPool(
					this.worldObj, this.xCoord, this.yCoord, this.zCoord);
		}

		TileEntityExternalMonitor monitor = getNeighbour(this.m_width - 1,
				this.m_height - 1);

		if (monitor != null) {
			int minX = Math.min(this.xCoord, monitor.xCoord);
			int minY = Math.min(this.yCoord, monitor.yCoord);
			int minZ = Math.min(this.zCoord, monitor.zCoord);

			int maxX = (minX == monitor.xCoord ? this.xCoord : monitor.xCoord) + 1;
			int maxY = (minY == monitor.yCoord ? this.yCoord : monitor.yCoord) + 1;
			int maxZ = (minZ == monitor.zCoord ? this.zCoord : monitor.zCoord) + 1;
			return AxisAlignedBB.getAABBPool().getAABB(minX, minY, minZ, maxX,
					maxY, maxZ);
		}

		return CCLights2.monitorBig.getCollisionBoundingBoxFromPool(
				this.worldObj, this.xCoord, this.yCoord, this.zCoord);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		nbttagcompound.setInteger("xIndex", this.m_xIndex);
		nbttagcompound.setInteger("yIndex", this.m_yIndex);
		nbttagcompound.setInteger("width", this.m_width);
		nbttagcompound.setInteger("height", this.m_height);
		nbttagcompound.setInteger("dir", this.m_dir);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		this.m_xIndex = nbttagcompound.getInteger("xIndex");
		this.m_yIndex = nbttagcompound.getInteger("yIndex");
		this.m_width = nbttagcompound.getInteger("width");
		this.m_height = nbttagcompound.getInteger("height");
		this.m_dir = nbttagcompound.getInteger("dir");
		dirty = true;
	}

	public void rebuildTerminal(Monitor copyFrom) {
		int termWidth = this.m_width * 32;
		int termHeight = this.m_height * 32;
		this.mon.resize(termWidth, termHeight);
		this.mon.removeAllGPUs();
		propogateTerminal();
	}

	@Override
	public Packet getDescriptionPacket() {
		dirty = true;
		return null;
	}

	public void propogateTerminal() {
		if (origin() == null) return;
		Monitor originTerminal = origin().mon;
		originTerminal.removeAllGPUs();
		originTerminal = new Monitor(m_width * 32, m_height * 32,getMonitorObject());
		origin().mon = originTerminal;
		for (int y = 0; y < this.m_height; y++) {
			for (int x = 0; x < this.m_width; x++) {
				TileEntityExternalMonitor monitor = getNeighbour(x, y);
				if (monitor != null) {
					{
						if ((x != 0) || (y != 0)) {
							monitor.mon.removeAllGPUs();
							monitor.mon = originTerminal;
						}
					}
				}
			}
		}
	}

	public int getDir() {
		return this.m_dir;
	}

	public void setDir(int _dir) {
		this.m_dir = _dir;
	}

	public int getRight() {
		int dir = getDir();
		switch (dir) {
		case 0:
			return 5;
		case 1:
			return 2;
		case 2:
			return 4;
		case 3:
			return 3;
		default:
			FMLLog.info("Dir: "+dir);
		}
		return dir;
	}

	public int getWidth() {
		return this.m_width;
	}

	public int getHeight() {
		return this.m_height;
	}

	public int getXIndex() {
		return this.m_xIndex;
	}

	public int getYIndex() {
		return this.m_yIndex;
	}

	public TileEntityExternalMonitor getSimilarMonitorAt(int x, int y, int z) {
		if ((y >= 0) && (y < this.worldObj.getHeight())) {
			if (this.worldObj.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
				TileEntity tile = this.worldObj.getBlockTileEntity(x, y, z);
				if ((tile != null) && ((tile instanceof TileEntityExternalMonitor))) {
					TileEntityExternalMonitor monitor = (TileEntityExternalMonitor) tile;
					if ((monitor.getDir() == getDir())
							&& (!monitor.m_destroyed) && (!monitor.m_ignoreMe)) {
						return monitor;
					}
				}
			}
		}

		return null;
	}

	public TileEntityExternalMonitor getNeighbour(int x, int y) {
		int right = getRight();
		int xOffset = -this.m_xIndex + x;
		return getSimilarMonitorAt(this.xCoord
				+ net.minecraft.util.Facing.offsetsXForSide[right] * xOffset,
				this.yCoord - this.m_yIndex + y, this.zCoord
						+ net.minecraft.util.Facing.offsetsZForSide[right]
						* xOffset);
	}

	public TileEntityExternalMonitor origin() {
		return getNeighbour(0, 0);
	}

	public void resize(int width, int height) {
		resize(width, height, false);
	}

	public void resize(int width, int height, boolean ignoreTerminals) {
		int right = getRight();
		int rightX = net.minecraft.util.Facing.offsetsXForSide[right];
		int rightZ = net.minecraft.util.Facing.offsetsZForSide[right];

		int totalConnections = 0;
		int maxConnections = 0;
		Monitor existingTerminal = null;
		int existingScale = 2;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				TileEntityExternalMonitor monitor = getSimilarMonitorAt(this.xCoord
						+ rightX * x, this.yCoord + y, this.zCoord + rightZ * x);
				if (monitor != null) {
					totalConnections += monitor.m_connections;
					if ((!ignoreTerminals)
							&& (monitor.m_connections > maxConnections)) {
						// synchronized (monitor.mon)
						{
							existingTerminal = monitor.mon;
						}
						maxConnections = monitor.m_connections;
					}

					monitor.m_totalConnections = 0;
					monitor.m_xIndex = x;
					monitor.m_yIndex = y;
					monitor.m_width = width;
					monitor.m_height = height;
					monitor.dirty = true;
				}
			}
		}

		this.m_totalConnections = totalConnections;
		rebuildTerminal(existingTerminal);

		this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord,
				this.zCoord, this.xCoord + rightX * width,
				this.yCoord + height, this.zCoord + rightZ * width);
	}

	public boolean mergeLeft() {
		TileEntityExternalMonitor left = getNeighbour(-1, 0);
		if ((left != null) && (left.m_yIndex == 0)
				&& (left.m_height == this.m_height)) {
			int width = left.m_width + this.m_width;
			if (width <= 16) {
				left.origin().resize(width, this.m_height);
				left.expand();
				return true;
			}
		}
		return false;
	}

	public boolean mergeRight() {
		TileEntityExternalMonitor right = getNeighbour(this.m_width, 0);
		if ((right != null) && (right.m_yIndex == 0)
				&& (right.m_height == this.m_height)) {
			int width = this.m_width + right.m_width;
			if (width <= 16) {
				origin().resize(width, this.m_height);
				expand();
				return true;
			}
		}
		return false;
	}

	public boolean mergeUp() {
		TileEntityExternalMonitor above = getNeighbour(0, this.m_height);
		if ((above != null) && (above.m_xIndex == 0)
				&& (above.m_width == this.m_width)) {
			int height = above.m_height + this.m_height;
			if (height <= 9) {
				origin().resize(this.m_width, height);
				expand();
				return true;
			}
		}
		return false;
	}

	public boolean mergeDown() {
		TileEntityExternalMonitor below = getNeighbour(0, -1);
		if ((below != null) && (below.m_xIndex == 0)
				&& (below.m_width == this.m_width)) {
			int height = this.m_height + below.m_height;
			if (height <= 9) {
				below.origin().resize(this.m_width, height);
				below.expand();
				return true;
			}
		}
		return false;
	}

	public void expand() {
		dirty = true;
		while ((mergeLeft()) || (mergeRight()) || (mergeUp()) || (mergeDown())) {
		}
		;
	}

	public void contractNeighbours() {
		this.mon.removeAllGPUs();
		this.m_ignoreMe = true;
		if (this.m_xIndex > 0) {
			TileEntityExternalMonitor left = getNeighbour(this.m_xIndex - 1,
					this.m_yIndex);
			if (left != null) {
				left.contract();
			}
		}
		if (this.m_xIndex + 1 < this.m_width) {
			TileEntityExternalMonitor right = getNeighbour(this.m_xIndex + 1,
					this.m_yIndex);
			if (right != null) {
				right.contract();
			}
		}
		if (this.m_yIndex > 0) {
			TileEntityExternalMonitor below = getNeighbour(this.m_xIndex,
					this.m_yIndex - 1);
			if (below != null) {
				below.contract();
			}
		}
		if (this.m_yIndex + 1 < this.m_height) {
			TileEntityExternalMonitor above = getNeighbour(this.m_xIndex,
					this.m_yIndex + 1);
			if (above != null) {
				above.contract();
			}
		}
		this.m_ignoreMe = false;
	}

	public void contract() {
		dirty = true;
		int height = this.m_height;
		int width = this.m_width;

		TileEntityExternalMonitor origin = origin();
		if (origin == null) {
			TileEntityExternalMonitor right = null;
			TileEntityExternalMonitor below = null;
			if (width > 1) {
				right = getNeighbour(1, 0);
			}
			if (height > 1) {
				below = getNeighbour(0, 1);
			}

			Monitor claimedTerminal = null;
			if (right != null) {
				right.resize(width - 1, 1);
				claimedTerminal = right.mon;
			}
			if (below != null) {
				below.resize(width, height - 1, claimedTerminal != null);
			}
			if (right != null) {
				right.expand();
			}
			if (below != null) {
				below.expand();
			}
			mon.removeAllGPUs();
			mon = new Monitor(32, 32,getMonitorObject());
			return;
		}

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				TileEntityExternalMonitor monitor = origin.getNeighbour(x, y);
				if (monitor == null) {
					TileEntityExternalMonitor above = null;
					TileEntityExternalMonitor left = null;
					TileEntityExternalMonitor right = null;
					TileEntityExternalMonitor below = null;

					Monitor claimedTerminal = null;
					if (y > 0) {
						above = origin;
						above.resize(width, y);
						claimedTerminal = above.mon;
					}
					if (x > 0) {
						left = origin.getNeighbour(0, y);
						left.resize(x, 1, claimedTerminal != null);
						if (claimedTerminal == null) {
							claimedTerminal = left.mon;
						}
					}
					if (x + 1 < width) {
						right = origin.getNeighbour(x + 1, y);
						right.resize(width - (x + 1), 1,
								claimedTerminal != null);
						if (claimedTerminal == null) {
							claimedTerminal = right.mon;
						}
					}
					if (claimedTerminal != null)
						claimedTerminal.removeAllGPUs();
					if (y + 1 < height) {
						below = origin.getNeighbour(0, y + 1);
						below.resize(width, height - (y + 1),
								claimedTerminal != null);
					}

					if (above != null) {
						above.expand();
					}
					if (left != null) {
						left.expand();
					}
					if (right != null) {
						right.expand();
					}
					if (below != null) {
						below.expand();
					}
					return;
				}
			}
		}
	}

	@Override
	public void updateEntity() {
		if (dirty || m_tts-- < 0) {
			// Send update packet
			PacketSenders.ExternalMonitorUpdate(xCoord, yCoord, zCoord, worldObj.provider.dimensionId,m_width, m_height,m_xIndex,m_yIndex,m_dir);
			dirty = false;
			m_tts = TICKS_TIL_SYNC;
		}
	}

	public void handleUpdatePacket(ByteArrayDataInput dat) {
		m_width = dat.readInt();
		m_height = dat.readInt();
		m_xIndex = dat.readInt();
		m_yIndex = dat.readInt();
		m_dir = dat.readInt();
		propogateTerminal();
	}

	@Override
	public String[] getMethodNames() {
		return new String[]{"getResolution","getDPM","getBlockResolution"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer,ILuaContext context, int method,Object[] arguments) throws Exception {
		switch (method)
		{
		case 0:
		{
			return new Object[]{mon.getWidth(),mon.getHeight()};
		}
		case 1:
		{
			return new Object[]{32};
		}
		case 2:
		{
			return new Object[]{m_width,m_height};
		}
		}
		return null;
	}

	@Override
	public String getType() {return "Monitor";}

	@Override
	public boolean canAttachToSide(int side) {return true;}

	@Override
	public void attach(IComputerAccess computer) {}

	@Override
	public void detach(IComputerAccess computer) {}
}
