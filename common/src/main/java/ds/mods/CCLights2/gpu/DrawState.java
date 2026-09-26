package ds.mods.CCLights2.gpu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;
import java.util.Deque;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

/**
 * All mutable drawing state of a GPU: colour, transform stack, line width, blend mode,
 * antialiasing and clip. It is applied to a Graphics2D before every primitive and kept in
 * sync between server and clients.
 */
public class DrawState {
	public static final int MAX_STACK = 256;
	public static final AffineTransform IDENTITY = new AffineTransform();
	public static final BasicStroke DEFAULT_STROKE = new BasicStroke(1f);

	public Color color = Color.white;
	public AffineTransform transform = new AffineTransform();
	public final Deque<AffineTransform> stack = new ArrayDeque<AffineTransform>();
	public float lineWidth = 1f;
	public BlendComposite blend = BlendComposite.NORMAL;
	public boolean antialias = false;
	/** Clip rectangle in texture coordinates, or null for no clip. */
	public Rectangle clip = null;

	public void reset() {
		color = Color.white;
		transform = new AffineTransform();
		stack.clear();
		lineWidth = 1f;
		blend = BlendComposite.NORMAL;
		antialias = false;
		clip = null;
	}

	public boolean push() {
		if (stack.size() >= MAX_STACK) return false;
		stack.push(transform);
		transform = new AffineTransform(transform);
		return true;
	}

	public boolean pop() {
		if (stack.isEmpty()) return false;
		transform = stack.pop();
		return true;
	}

	/** Installs this state on a graphics context. */
	public void apply(Graphics2D g) {
		g.setTransform(IDENTITY);
		g.setClip(clip);
		g.setTransform(transform);
		g.setColor(color);
		g.setComposite(blend.toAwt());
		g.setStroke(lineWidth == 1f ? DEFAULT_STROKE : new BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, antialias ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	/** True when drawing a texture with the current colour would change it (tint or alpha). */
	public boolean isTinted() {
		return color.getRGB() != 0xFFFFFFFF;
	}

	// ---- network sync ----

	public void write(ByteArrayDataOutput out) {
		out.writeInt(color.getRGB());
		writeMatrix(out, transform);
		out.writeInt(stack.size());
		// Deque iteration is top-first; write bottom-first so reading can push in order.
		AffineTransform[] arr = stack.toArray(new AffineTransform[0]);
		for (int i = arr.length - 1; i >= 0; i--) writeMatrix(out, arr[i]);
		out.writeFloat(lineWidth);
		out.writeByte(blend.ordinal());
		out.writeBoolean(antialias);
		out.writeBoolean(clip != null);
		if (clip != null) {
			out.writeInt(clip.x); out.writeInt(clip.y); out.writeInt(clip.width); out.writeInt(clip.height);
		}
	}

	public void read(ByteArrayDataInput in) {
		color = new Color(in.readInt(), true);
		transform = readMatrix(in);
		stack.clear();
		int n = in.readInt();
		for (int i = 0; i < n; i++) stack.push(readMatrix(in));
		lineWidth = in.readFloat();
		int b = in.readByte();
		blend = b >= 0 && b < BlendComposite.values().length ? BlendComposite.values()[b] : BlendComposite.NORMAL;
		antialias = in.readBoolean();
		if (in.readBoolean()) {
			clip = new Rectangle(in.readInt(), in.readInt(), in.readInt(), in.readInt());
		} else {
			clip = null;
		}
	}

	private static void writeMatrix(ByteArrayDataOutput out, AffineTransform t) {
		double[] m = new double[6];
		t.getMatrix(m);
		for (double v : m) out.writeDouble(v);
	}

	private static AffineTransform readMatrix(ByteArrayDataInput in) {
		double[] m = new double[6];
		for (int i = 0; i < 6; i++) m[i] = in.readDouble();
		return new AffineTransform(m);
	}
}
