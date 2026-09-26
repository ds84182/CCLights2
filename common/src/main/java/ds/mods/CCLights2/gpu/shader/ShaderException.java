package ds.mods.CCLights2.gpu.shader;

/** A compile-time or run-time shader error, with the source line when known. */
public class ShaderException extends Exception {
	public final int line;

	public ShaderException(String message, int line) {
		super(line > 0 ? "line " + line + ": " + message : message);
		this.line = line;
	}

	public ShaderException(String message) {
		this(message, 0);
	}
}
