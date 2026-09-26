package ds.mods.CCLights2.block.entity;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import ds.mods.CCLights2.gpu.Monitor;
import net.minecraft.core.BlockPos;

/**
 * The object {@code gpu.getMonitor()} hands to Lua (it is {@link Monitor#obj}). CC: Tweaked turns the
 * {@code @LuaFunction} methods into a table of functions. {@link #owner()} is the block entity that
 * created the screen: the monitor itself, the origin of an external monitor wall, or the transceiver.
 */
public class MonitorLuaObject {
	protected final MonitorBlockEntity owner;

	public MonitorLuaObject(MonitorBlockEntity owner) {
		this.owner = owner;
	}

	public MonitorBlockEntity owner() {
		return owner;
	}

	@LuaFunction
	public final MethodResult getResolution() {
		Monitor m = owner.getMonitor();
		return m == null ? MethodResult.of() : MethodResult.of(m.getWidth(), m.getHeight());
	}

	@LuaFunction
	public final MethodResult getSize() {
		return getResolution();
	}

	@LuaFunction
	public final String getType() {
		return owner.getMonitorTypeName();
	}

	@LuaFunction
	public final MethodResult getPosition() {
		BlockPos p = owner.getBlockPos();
		return MethodResult.of(p.getX(), p.getY(), p.getZ());
	}
}
