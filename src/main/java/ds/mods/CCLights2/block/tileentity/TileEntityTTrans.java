package ds.mods.CCLights2.block.tileentity;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.gpu.LuaArgs;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.utils.TabMesg;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * Tablet transceiver: a 512x288 monitor that tablets paired with it show wirelessly.
 */
public class TileEntityTTrans extends TileEntityMonitor implements IPeripheral {
	public static final int WIDTH = 16 * 32;
	public static final int HEIGHT = 9 * 32;

	public UUID id = UUID.randomUUID();
	public final List<UUID> tablets = new ArrayList<UUID>();
	private final List<IComputerAccess> computers = new java.util.concurrent.CopyOnWriteArrayList<IComputerAccess>();
	private boolean dirty = false;

	/** Raises an event on computers attached to this transceiver directly and on those behind its GPUs. */
	public void queueEvent(String event, Object[] args) {
		for (IComputerAccess c : computers) {
			Object[] full = java.util.Arrays.copyOf(args, args.length + 1);
			full[args.length] = c.getAttachmentName();
			c.queueEvent(event, full);
		}
		Monitor m = getMonitor();
		if (m == null) return;
		for (ds.mods.CCLights2.gpu.GPU g : m.gpus) {
			if (g.tile != null) g.tile.queueEvent(event, args);
		}
	}

	public TileEntityTTrans() {
		super(false);
		mon = new Monitor(WIDTH, HEIGHT, getMonitorObject());
		mon.tex.fill(Color.black);
		mon.tex.texUpdate();
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void validate() {
		super.validate();
		if (worldObj != null && worldObj.isRemote) TabMesg.setPosition(id, xCoord, yCoord, zCoord);
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

	/** Pairs a tablet with this transceiver. */
	public void connectTablet(UUID tablet) {
		if (!tablets.contains(tablet)) {
			tablets.add(tablet);
			dirty = true;
		}
	}

	public void disconnectAll() {
		if (!tablets.isEmpty()) {
			tablets.clear();
			dirty = true;
		}
	}

	public boolean isPaired(UUID tablet) {
		return tablets.contains(tablet);
	}

	/** Tablets work within a configurable radius instead of the usual GUI reach. */
	@Override
	public boolean canInteract(EntityPlayer player) {
		double r = Config.tabletRange + 1;
		return player.worldObj == worldObj && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= r * r;
	}

	@Override
	protected String monitorTypeName() {
		return "tablet";
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if (nbt.getString("uuid").length() > 0) {
			try {
				id = UUID.fromString(nbt.getString("uuid"));
			} catch (IllegalArgumentException ignored) {}
		}
		if (worldObj != null && worldObj.isRemote) TabMesg.setPosition(id, xCoord, yCoord, zCoord);
		tablets.clear();
		NBTTagList lst = nbt.getTagList("tablets", 8);
		for (int i = 0; i < lst.tagCount(); i++) {
			try {
				tablets.add(UUID.fromString(lst.getStringTagAt(i)));
			} catch (IllegalArgumentException ignored) {}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setString("uuid", id.toString());
		NBTTagList lst = new NBTTagList();
		for (UUID t : tablets) lst.appendTag(new NBTTagString(t.toString()));
		nbt.setTag("tablets", lst);
	}

	// ------------------------------------------------------------------ peripheral

	@Override
	public String getType() {
		return "TabletTransceiver";
	}

	@Override
	public String[] getMethodNames() {
		return new String[] { "getResolution", "getNumberOfTablets", "getTabletUUID", "disconnect" };
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args) throws LuaException {
		switch (method) {
		case 0:
			return new Object[] { mon.getWidth(), mon.getHeight() };
		case 1:
			return new Object[] { tablets.size() };
		case 2: {
			int i = LuaArgs.getInt(args, 0, "getTabletUUID");
			if (i < 1 || i > tablets.size()) throw new LuaException("bad argument #1 to 'getTabletUUID' (index out of range)");
			return new Object[] { tablets.get(i - 1).toString() };
		}
		case 3:
			disconnectAll();
			return null;
		default:
			return null;
		}
	}

	@Override
	public void attach(IComputerAccess computer) {
		computers.add(computer);
	}

	@Override
	public void detach(IComputerAccess computer) {
		computers.remove(computer);
	}

	@Override
	public boolean equals(IPeripheral other) {
		return other == this;
	}
}
