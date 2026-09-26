package ds.mods.CCLights2.gpu;

import ds.mods.CCLights2.CommandEnum;

/**
 * A single GPU command with its already-converted arguments.
 * Argument values are Integer, Double, String, Boolean, int[] or byte[]; see {@link ds.mods.CCLights2.network.Serialize}.
 */
public class DrawCMD {
	public static final Object[] NO_ARGS = new Object[0];

	public CommandEnum cmd;
	public Object[] args = NO_ARGS;

	public DrawCMD() {}

	public DrawCMD(CommandEnum cmd, Object... args) {
		this.cmd = cmd;
		this.args = args == null ? NO_ARGS : args;
	}

	public int getInt(int i) {
		return ((Number) args[i]).intValue();
	}

	public double getDouble(int i) {
		return ((Number) args[i]).doubleValue();
	}

	public boolean getBool(int i) {
		return (Boolean) args[i];
	}

	public String getString(int i) {
		return String.valueOf(args[i]);
	}
}
