package ds.mods.CCLights2.network;

import java.util.Arrays;

import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;

public class SplitPacket {
	public int packetAmount;
	public short id;
	public byte[][] packets;
	public IPacketHandler packetHandler;
	public INetworkManager manager;
	public Player player;
	public String channel;
	public boolean finish;
	
	public SplitPacket(byte[] data, IPacketHandler packetHandler, INetworkManager manager, Player p, String c)
	{
		this.packetHandler = packetHandler;
		this.manager = manager;
		this.player = p;
		this.channel = c;
		ByteArrayDataInput in = ByteStreams.newDataInput(data);
		if (in.readByte() != -1)
		{
			throw new IllegalArgumentException("This is not a split packet!");
		}
		id = in.readShort();
		if (in.readInt() != 0)
		{
			throw new IllegalArgumentException("This is not the first packet in the split packet!");
		}
		packetAmount = in.readInt();
		if (packetAmount != 1)
		{
			packets = new byte[packetAmount][];
			//Read the header and take out the packet data
			byte[] realData = Arrays.copyOfRange(data, 11, data.length);
			packets[0] = realData;
		}
		else
		{
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = channel;
			packet.data = Arrays.copyOfRange(data, 11, data.length);
			packet.length = packet.data.length;
			this.finish = true;
			packetHandler.onPacketData(manager, packet, player);
		}
	}
	
	public void addPacket(byte[] data)
	{
		ByteArrayDataInput in = ByteStreams.newDataInput(data);
		if (in.readByte() != -1)
		{
			throw new IllegalArgumentException("This is not a split packet!");
		}
		id = in.readShort();
		int pat = in.readInt();
		packetAmount = in.readInt();
		//Read the header and take out the packet data
		byte[] realData = Arrays.copyOfRange(data, 11, data.length);
		packets[pat] = realData;
		if (pat+1 == packetAmount)
		{
			//Piece the packet together
			int size = 0;
			for (byte[] dat : packets)
			{
				size+=dat.length;
			}
			byte[] realdata = new byte[size];
			int at = 0;
			for (byte[] dat : packets)
			{
				for (int i = 0; i<dat.length; i++)
				{
					realdata[at] = dat[i];
					at++;
				}
			}
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = channel;
			packet.data = realdata;
			packet.length = realdata.length;
			this.finish = true;
			packetHandler.onPacketData(manager, packet, player);
		}
	}
}
