package ds.mods.CCLights2.network;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.network.packet.Packet250CustomPayload;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;

public class PacketSplitter {
	public static void sendPacketToAllInDimension(Packet250CustomPayload packet, int dimId)
	{
		if (packet.length<=32768)
		{
			PacketDispatcher.sendPacketToAllInDimension(packet, dimId);
			return;
		}
		//Each packet contains the byte -1 and a short as a random value. We make sure that that short is not on the send list for each player.
		//Packets are split into packets of 32767-3 (3 for that byte and short)
		int numOfPackets = (int) Math.ceil(packet.data.length/(32767-11D));
		short rand = (short) new Random().nextInt();
		for (int i = 0; i<numOfPackets; i++)
		{
			byte[] copy = Arrays.copyOfRange(packet.data, (32767-11)*i, ((32767-11)*i)+Math.min(32767-11, packet.data.length-((32767-11)*i)));
			ByteArrayDataOutput out = ByteStreams.newDataOutput(copy.length+11);
			out.writeByte(-1);
			out.writeShort(rand);
			out.writeInt(i);
			out.writeInt(numOfPackets);
			out.write(copy);
			PacketDispatcher.sendPacketToAllInDimension(new Packet250CustomPayload("CCLights2",out.toByteArray()), dimId);
		}
	}
	
	public static void sendPacketToPlayer(Packet250CustomPayload packet, Player player)
	{
		if (packet.length<=32768)
		{
			PacketDispatcher.sendPacketToPlayer(packet, player);
			return;
		}
		//Each packet contains the byte -1 and a short as a random value. We make sure that that short is not on the send list for each player.
		//Packets are split into packets of 32767-3 (3 for that byte and short)
		int numOfPackets = (int) Math.ceil(packet.data.length/(32767-11D));
		short rand = (short) new Random().nextInt();
		for (int i = 0; i<numOfPackets; i++)
		{
			byte[] copy = Arrays.copyOfRange(packet.data, (32767-11)*i, ((32767-11)*i)+Math.min(32767-11, packet.data.length-((32767-11)*i)));
			ByteArrayDataOutput out = ByteStreams.newDataOutput(copy.length+11);
			out.writeByte(-1);
			out.writeShort(rand);
			out.writeInt(i);
			out.writeInt(numOfPackets);
			out.write(copy);
			PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload("CCLights2",out.toByteArray()), player);
		}
	}
}
