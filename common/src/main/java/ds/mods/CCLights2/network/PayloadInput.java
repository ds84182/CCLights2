package ds.mods.CCLights2.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.common.io.ByteArrayDataInput;

/**
 * A {@link ByteArrayDataInput} over a byte array that knows how many bytes are left, so
 * {@link Serialize} can reject length prefixes larger than the message before allocating.
 * Like Guava's implementation, reading past the end throws {@link IllegalStateException}.
 * Minecraft-free.
 */
public final class PayloadInput implements ByteArrayDataInput {
	private final byte[] data;
	private int pos;
	private final int end;

	public PayloadInput(byte[] data) {
		this(data, 0);
	}

	public PayloadInput(byte[] data, int start) {
		if (start < 0 || start > data.length) throw new IllegalArgumentException("start " + start);
		this.data = data;
		this.pos = start;
		this.end = data.length;
	}

	/** Bytes not read yet. */
	public int remaining() {
		return end - pos;
	}

	private int take(int n) {
		if (n < 0 || n > end - pos) throw new IllegalStateException("payload truncated (need " + n + ", have " + (end - pos) + ")");
		int p = pos;
		pos += n;
		return p;
	}

	@Override
	public void readFully(byte[] b) {
		readFully(b, 0, b.length);
	}

	@Override
	public void readFully(byte[] b, int off, int len) {
		int p = take(len);
		System.arraycopy(data, p, b, off, len);
	}

	@Override
	public int skipBytes(int n) {
		int k = Math.max(0, Math.min(n, end - pos));
		pos += k;
		return k;
	}

	@Override
	public boolean readBoolean() {
		return data[take(1)] != 0;
	}

	@Override
	public byte readByte() {
		return data[take(1)];
	}

	@Override
	public int readUnsignedByte() {
		return data[take(1)] & 0xFF;
	}

	@Override
	public short readShort() {
		int p = take(2);
		return (short) (((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF));
	}

	@Override
	public int readUnsignedShort() {
		return readShort() & 0xFFFF;
	}

	@Override
	public char readChar() {
		return (char) readShort();
	}

	@Override
	public int readInt() {
		int p = take(4);
		return ((data[p] & 0xFF) << 24) | ((data[p + 1] & 0xFF) << 16) | ((data[p + 2] & 0xFF) << 8) | (data[p + 3] & 0xFF);
	}

	@Override
	public long readLong() {
		long hi = readInt() & 0xFFFFFFFFL;
		long lo = readInt() & 0xFFFFFFFFL;
		return (hi << 32) | lo;
	}

	@Override
	public float readFloat() {
		return Float.intBitsToFloat(readInt());
	}

	@Override
	public double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	@Override
	public String readLine() {
		throw new UnsupportedOperationException("readLine");
	}

	@Override
	public String readUTF() {
		try {
			return DataInputStream.readUTF(this);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
