package ds.mods.CCLights2.utils;

import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.network.packet.Packet250CustomPayload;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.PacketDispatcher;
import ds.mods.CCLights2.converter.ConvertBoolean;
import ds.mods.CCLights2.converter.ConvertDouble;
import ds.mods.CCLights2.converter.ConvertFloat;
import ds.mods.CCLights2.converter.ConvertInteger;
import ds.mods.CCLights2.converter.ConvertString;
import ds.mods.CCLights2.serialize.Serialize;
import ds.mods.CCLights2.utils.LuaMethod.Type;

public class LuaMethodUtils {
	public static String[] getMethodNames(Class clazz)
	{
		StringBuilder sb = new StringBuilder();
		boolean addC = false;
		for (Method m : clazz.getMethods())
		{
			LuaMethod lm = m.getAnnotation(LuaMethod.class);
			if (lm != null)
			{
				if (addC)
					sb.append(',');
				addC = true;
				sb.append(lm.name());
			}
		}
		return sb.toString().split(",");
	}
	
	public static Object[] runMethod(Class<?> methods, String method, Object headObject, Object[] extraData, boolean server) throws Exception
	{
		for (Method m : methods.getMethods())
		{
			LuaMethod lm = m.getAnnotation(LuaMethod.class);
			if (lm != null)
			{
				if (lm.name().equals(method))
				{
					//Ok, found the calling method.//
					//Now we look at the extraData to see if it matches the given args in the LuaMethod thing
					Object[] args = null;
					Type[] acceptArgs = lm.args();
					if (acceptArgs.length == 1 && acceptArgs[0] == Type.OBJECTS)
					{
						//It wants all of extraData...
						args = new Object[]{headObject,extraData};
					}
					else
					{
						args = new Object[acceptArgs.length+1];
						args[0] = headObject;
						for (int i = 0; i<acceptArgs.length; i++)
						{
							switch (acceptArgs[i])
							{
							case BOOLEAN:
								args[i+1] = ConvertBoolean.convert(extraData[i]);
								break;
							case DOUBLE:
								args[i+1] = ConvertDouble.convert(extraData[i]);
								break;
							case FLOAT:
								args[i+1] = ConvertFloat.convert(extraData[i]);
								break;
							case INT:
								args[i+1] = ConvertInteger.convert(extraData[i]);
								break;
							case LuaObject:
								throw new Exception("Not IMPL");
								//break;
							case MAP:
								args[i+1] = (Map) extraData[i];
								break;
							case NULL:
								args[i+1] = null; //Meh, I guess :P
								break;
							case OBJECT:
								args[i+1] = extraData[i];
								break;
							case OBJECTS:
								throw new Exception("Cannot do this!");
								//break;
							case STRING:
								args[i+1] = ConvertString.convert(extraData[i]);
								break;
							default:
								break;
							}
						}
					}
					Object retNC = m.invoke(null, args);
					Object[] ret = null;
					switch (lm.ret())
					{
					case BOOLEAN:
					case DOUBLE:
					case FLOAT:
					case INT:
					case LuaObject:
					case MAP:
					case OBJECT:
					case STRING:
						ret = new Object[]{retNC};
					case NULL:
						ret = new Object[0];
						break;
					case OBJECTS:
						ret = (Object[]) retNC;
						break;
					default:
						break;
					}
					
					if (lm.networked() && server)
					{
						//TODO: Send via network
						ByteArrayDataOutput out = ByteStreams.newDataOutput();
						out.writeUTF(method);
						out.writeUTF(methods.getName());
						out.writeInt(args.length);
						for (int i=0; i<args.length; i++)
						{
							Serialize.serialize(out, args[i]);
						}
						byte[] data = out.toByteArray();
						//FMLLog.info("%s serializes to %d bytes with %s arguments.",method,data.length,args.length);
						PacketDispatcher.sendPacketToAllPlayers(new Packet250CustomPayload("NetMethod", data));
					}
					return ret;
				}
			}
		}
		return null;
	}
}
