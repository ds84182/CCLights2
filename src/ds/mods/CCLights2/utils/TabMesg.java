package ds.mods.CCLights2.utils;

import java.util.HashMap;
import java.util.Stack;
import java.util.UUID;

public class TabMesg {
	static HashMap<UUID,Stack<Message>> msglst = new HashMap<UUID, Stack<Message>>();
	static HashMap<UUID,HashMap<String,Object>> tabvars = new HashMap<UUID, HashMap<String,Object>>();
	
	public static void pushMessage(UUID tablet, Message m)
	{
		if (!msglst.containsKey(tablet))
		{
			msglst.put(tablet, new Stack<TabMesg.Message>());
		}
		msglst.get(tablet).push(m);
	}
	
	public static Message popMessage(UUID tablet)
	{
		if (!msglst.containsKey(tablet))
		{
			return null;
		}
		Stack<Message> m = msglst.get(tablet);
		return m.empty() ? null : m.pop();
	}
	
	public static void setTabVar(UUID tablet, String key, Object val)
	{
		if (!tabvars.containsKey(tablet))
		{
			tabvars.put(tablet, new HashMap<String, Object>());
		}
		tabvars.get(tablet).put(key, val);
	}
	
	public static Object getTabVar(UUID tablet, String key)
	{
		if (!tabvars.containsKey(tablet))
		{
			tabvars.put(tablet, new HashMap<String, Object>());
			return null;
		}
		return tabvars.get(tablet).get(key);
	}
	
	public static class Message
	{
		public String name;
		public Object a;
		public Object b;
		public Object c;
		public Object d;
		
		public Message(String s)
		{
			name = s;
		}
		
		public Message(String s, Object a)
		{
			name = s;
			this.a = a;
		}
	}
}
