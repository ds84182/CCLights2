package ds.mods.CCLights2.serialize;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public interface ISerializer {
	public void write(Object o, ByteArrayDataOutput dat);
	public Object read(ByteArrayDataInput dat);
}
