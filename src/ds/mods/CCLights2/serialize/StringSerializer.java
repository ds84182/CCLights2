package ds.mods.CCLights2.serialize;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class StringSerializer implements ISerializer {

	@Override
	public void write(Object o, ByteArrayDataOutput dat) {
		dat.writeUTF((String) o);
	}

	@Override
	public Object read(ByteArrayDataInput dat) {
		return dat.readUTF();
	}

}
