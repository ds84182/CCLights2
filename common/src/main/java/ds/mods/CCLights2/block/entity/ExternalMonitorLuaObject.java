package ds.mods.CCLights2.block.entity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;

/** Lua object of an external monitor wall; created by the wall's origin block. */
public class ExternalMonitorLuaObject extends MonitorLuaObject {
	public ExternalMonitorLuaObject(ExternalMonitorBlockEntity owner) {
		super(owner);
	}

	private ExternalMonitorBlockEntity wall() {
		return (ExternalMonitorBlockEntity) owner;
	}

	/** Screen pixels per block ("dots per metre") at the current scale. */
	@LuaFunction
	public final int getDPM() {
		return Math.max(1, ExternalMonitorBlockEntity.pixelsPerBlock() / wall().getScale());
	}

	/** Size of the wall in blocks. */
	@LuaFunction
	public final MethodResult getBlockResolution() {
		ExternalMonitorBlockEntity w = wall();
		return MethodResult.of(w.getWidthBlocks(), w.getHeightBlocks());
	}

	/** Changes the pixel size of the whole wall (1 = full resolution ... 8). Runs on the server thread. */
	@LuaFunction(mainThread = true)
	public final boolean setScale(int scale) throws LuaException {
		if (scale < 1 || scale > ExternalMonitorBlockEntity.MAX_SCALE)
			throw new LuaException("setScale: scale must be between 1 and " + ExternalMonitorBlockEntity.MAX_SCALE);
		return wall().setScale(scale);
	}

	@LuaFunction
	public final int getScale() {
		return wall().getScale();
	}
}
