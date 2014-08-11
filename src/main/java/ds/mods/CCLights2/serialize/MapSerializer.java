package ds.mods.CCLights2.serialize;

import java.util.HashMap;
import java.util.Map;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public class MapSerializer implements ISerializer {

	@Override
	public void write(Object o, ByteArrayDataOutput dat) {
		Map m = (Map)o;
		dat.writeInt(m.size());
		for (Object k : m.keySet())
		{
			Serialize.serialize(dat, k);
			Serialize.serialize(dat, m.get(k));
		}
	}

	@Override
	public Object read(ByteArrayDataInput dat) {
		Map m = new HashMap();
		int size = dat.readInt();
		for (int i=0; i<size; i++)
		{
			m.put(Serialize.unserialize(dat), Serialize.unserialize(dat));
		}
		return m;
	}

}
