package ds.mods.CCLights2.block.entity;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.gpu.GpuLuaApi;

/**
 * CC: Tweaked peripheral of the GPU block. The method table is {@link GpuLuaApi#METHOD_NAMES}; calls go
 * to {@code GpuLuaApi.call(method, args.getAll(), importSource)} on the ComputerCraft thread (GpuLuaApi
 * takes the GPU lock). One instance per block entity, so {@link #equals(IPeripheral)} is stable.
 */
public class GpuPeripheral implements IDynamicPeripheral {
	private final GpuBlockEntity be;

	public GpuPeripheral(GpuBlockEntity be) {
		this.be = be;
	}

	public GpuBlockEntity blockEntity() {
		return be;
	}

	@Override
	public String[] getMethodNames() {
		return GpuLuaApi.METHOD_NAMES;
	}

	@Override
	public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
		Object[] result = be.callLua(computer, method, arguments.getAll());
		return result == null ? MethodResult.of() : MethodResult.of(result);
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
	public String getType() {
		return "GPU";
	}

	@Override
	public Set<String> getAdditionalTypes() {
		return Set.of("gpu");
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof GpuPeripheral o && o.be == be;
	}

	@Override
	public Object getTarget() {
		return be;
	}
}
