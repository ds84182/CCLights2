package ds.mods.CCLights2;

/**
 * Every method the GPU peripheral exposes to Lua, in the order they are exposed.
 * The ordinal is used on the wire, so client and server must run the same build.
 */
public enum CommandEnum {
	// ---- drawing commands: executed on the server and replicated to every client ----
	Fill("fill", true),
	Plot("plot", true),
	CreateTexture("createTexture", true),
	DrawTexture("drawTexture", true),
	DrawTextureScaled("drawTextureScaled", true),
	DrawText("drawText", true),
	BindTexture("bindTexture", true),
	FreeTexture("freeTexture", true),
	Line("line", true),
	Rectangle("rectangle", true),
	FilledRectangle("filledRectangle", true),
	RoundRectangle("roundRectangle", true),
	FilledRoundRectangle("filledRoundRectangle", true),
	Triangle("triangle", true),
	FilledTriangle("filledTriangle", true),
	Oval("oval", true),
	FilledOval("filledOval", true),
	Arc("arc", true),
	FilledArc("filledArc", true),
	Polygon("polygon", true),
	FilledPolygon("filledPolygon", true),
	Curve("curve", true),
	Bezier("bezier", true),
	GradientRectangle("gradientRectangle", true),
	SetPixels("setPixels", true),
	FlipVertically("flipVertically", true),
	FlipHorizontally("flipHorizontally", true),
	ResizeTexture("resizeTexture", true),
	CopyTexture("copyTexture", true),
	Import("import", true),
	Translate("translate", true),
	Rotate("rotate", true),
	RotateAround("rotateAround", true),
	Scale("scale", true),
	Shear("shear", true),
	Push("push", true),
	Pop("pop", true),
	Origin("origin", true),
	Blur("blur", true),
	GaussianBlur("gaussianBlur", true),
	Glow("glow", true),
	Sharpen("sharpen", true),
	Invert("invert", true),
	Grayscale("grayscale", true),
	ClearRectangle("clearRectangle", true),
	SetColor("setColor", true),
	SetLineWidth("setLineWidth", true),
	SetBlendMode("setBlendMode", true),
	SetAntialias("setAntialias", true),
	SetClip("setClip", true),
	ResetClip("resetClip", true),
	CreateShader("createShader", true),
	FreeShader("freeShader", true),
	RunShader("runShader", true),
	Reset("reset", true),

	// ---- queries and control: answered by the server only ----
	GetFreeMemory("getFreeMemory", false),
	GetTotalMemory("getTotalMemory", false),
	GetUsedMemory("getUsedMemory", false),
	GetSize("getSize", false),
	GetPixels("getPixels", false),
	GetBindedTexture("getBindedTexture", false),
	GetTextWidth("getTextWidth", false),
	GetTextHeight("getTextHeight", false),
	GetMonitor("getMonitor", false),
	Export("export", false),
	GetColor("getColor", false),
	GetLineWidth("getLineWidth", false),
	GetBlendMode("getBlendMode", false),
	GetAntialias("getAntialias", false),
	GetClip("getClip", false),
	ListTextures("listTextures", false),
	SetUniform("setUniform", false),
	GetShaderUniforms("getShaderUniforms", false),
	ListShaders("listShaders", false),
	StartFrame("startFrame", false),
	EndFrame("endFrame", false);

	/** Name of the method as seen from Lua. */
	public final String luaName;
	/** True when the command changes texture contents or GPU state and therefore has to be sent to clients. */
	public final boolean replicated;

	CommandEnum(String luaName, boolean replicated) {
		this.luaName = luaName;
		this.replicated = replicated;
	}

	public static final CommandEnum[] VALUES = values();

	/** Old method names that are still accepted. */
	public static final String[][] ALIASES = {
		{ "flipTextureV", "flipVertically" },
		{ "flipTextureH", "flipHorizontally" },
		{ "clearRect", "clearRectangle" },
		{ "getPixelColor", "getPixels" },
	};
}
