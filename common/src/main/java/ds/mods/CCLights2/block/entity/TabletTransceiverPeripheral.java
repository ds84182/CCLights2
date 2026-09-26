package ds.mods.CCLights2.block.entity;

import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.gpu.Monitor;

/**
 * CC: Tweaked peripheral of the tablet transceiver (the peripheral half of TileEntityTTrans). A separate
 * object because BlockEntity#getType() clashes with IPeripheral#getType(). One instance per block
 * entity, so {@link #equals(IPeripheral)} is stable.
 */
public class TabletTransceiverPeripheral implements IPeripheral {
	private final TabletTransceiverBlockEntity be;

	public TabletTransceiverPeripheral(TabletTransceiverBlockEntity be) {
		this.be = be;
	}

	public TabletTransceiverBlockEntity blockEntity() {
		return be;
	}

	@Override
	public String getType() {
		return "TabletTransceiver";
	}

	@Override
	public Set<String> getAdditionalTypes() {
		return Set.of("tablet_transceiver");
	}

	@Override
	public void attach(IComputerAccess computer) {
		be.attach(computer);
	}

	@Override
	public void detach(IComputerAccess computer) {
		be.detach(computer);
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof TabletTransceiverPeripheral o && o.be == be;
	}

	@Override
	public Object getTarget() {
		return be;
	}

	// ------------------------------------------------------------------ Lua methods

	@LuaFunction
	public final MethodResult getResolution() {
		Monitor m = be.getMonitor();
		return m == null
				? MethodResult.of(TabletTransceiverBlockEntity.WIDTH, TabletTransceiverBlockEntity.HEIGHT)
				: MethodResult.of(m.getWidth(), m.getHeight());
	}

	@LuaFunction
	public final int getNumberOfTablets() {
		return be.getTabletCount();
	}

	/** Id of the i-th paired tablet (1-based). */
	@LuaFunction
	public final String getTabletUUID(int index) throws LuaException {
		UUID id = be.getTablet(index - 1);
		if (id == null) throw new LuaException("bad argument #1 to 'getTabletUUID' (index out of range)");
		return id.toString();
	}

	/** Unpairs every tablet. */
	@LuaFunction(mainThread = true)
	public final void disconnect() {
		be.disconnectAll();
	}
}
