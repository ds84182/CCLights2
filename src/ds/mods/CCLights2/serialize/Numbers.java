package ds.mods.CCLights2.serialize;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class Numbers {
	public static class ByteSerializer implements ISerializer
	{

		@Override
		public void write(Object o, ByteArrayDataOutput dat) {
			dat.writeByte((Byte)o);
		}

		@Override
		public Object read(ByteArrayDataInput dat) {
			return dat.readByte();
		}
		
	}
	
	public static class ShortSerializer implements ISerializer
	{
	
		@Override
		public void write(Object o, ByteArrayDataOutput dat) {
			dat.writeShort((Short)o);
		}
	
		@Override
		public Object read(ByteArrayDataInput dat) {
			return dat.readShort();
		}
		
	}

	public static class IntegerSerializer implements ISerializer
	{

		@Override
		public void write(Object o, ByteArrayDataOutput dat) {
			dat.writeInt((Integer)o);
		}

		@Override
		public Object read(ByteArrayDataInput dat) {
			return dat.readInt();
		}
		
	}
	
	public static class FloatSerializer implements ISerializer
	{

		@Override
		public void write(Object o, ByteArrayDataOutput dat) {
			dat.writeFloat((Float)o);
		}

		@Override
		public Object read(ByteArrayDataInput dat) {
			return dat.readFloat();
		}
		
	}
	
	public static class DoubleSerializer implements ISerializer
	{

		@Override
		public void write(Object o, ByteArrayDataOutput dat) {
			dat.writeDouble((Double)o);
		}

		@Override
		public Object read(ByteArrayDataInput dat) {
			return dat.readDouble();
		}
		
	}
	
	public static class LongSerializer implements ISerializer
	{

		@Override
		public void write(Object o, ByteArrayDataOutput dat) {
			dat.writeLong((Long)o);
		}

		@Override
		public Object read(ByteArrayDataInput dat) {
			return dat.readLong();
		}
		
	}
}
