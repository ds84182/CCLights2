package ds.mods.CCLights2.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;

public class PacketChunker {

	private byte packetId = 0;

	private final HashMap<Byte, byte[][]> packetStack = new HashMap<Byte, byte[][]>();

	public final static PacketChunker instance = new PacketChunker();

	/***
	 * Convert a byte array into one or more packets
	 * 
	 * @param the
	 *            byte array
	 * @return the list of chunks
	 * @throws IOException
	 */
	public Packet[] createPackets(String channel, byte[] input) throws IOException {
		//gzip.jpg
		ByteArrayOutputStream dataToCompress = new ByteArrayOutputStream();
		GZIPOutputStream zipStream = new GZIPOutputStream(dataToCompress);
		zipStream.write(input);
		zipStream.close();
		byte[] data = dataToCompress.toByteArray();
		dataToCompress.close();
		
		//CCLights2.debug("packetsender: "+(data.length-input.length));
		
		int start = 0;
		short maxChunkSize = Short.MAX_VALUE - 100;
		byte numChunks = (byte)Math.ceil(data.length / (double)maxChunkSize);
		Packet[] packets = new Packet[numChunks];
		final byte META_LENGTH = 4;

		for (byte i = 0; i < numChunks; i++) {

			// size of the current chunk
			int chunkSize = Math.min(data.length - start, maxChunkSize);

			// make a new byte array but leave space for the meta
			byte[] chunk = new byte[META_LENGTH + chunkSize];

			// set the chunk metadata: total number of chunks, current chunk
			// index, packetId to match chunks together
			chunk[0] = PacketHandler.NET_SPLITPACKET;
			chunk[1] = numChunks;
			chunk[2] = i;
			chunk[3] = packetId;

			// copy part of the data across
			System.arraycopy(data, start, chunk, META_LENGTH, chunkSize);

			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = channel;
			packet.data = chunk;
			packet.length = chunk.length;
			packets[i] = packet;
			start += chunkSize;
		}
		packetId++;
		return packets;
	}

	/***
	 * Get the bytes from the packet. If the total packet is not yet complete
	 * (and we're waiting for more to complete the sequence), We return null.
	 * Otherwise we return the full byte array
	 * 
	 * @param one
	 *            of the packets
	 * @return the full byte array
	 * @throws IOException
	 */
	public byte[] getBytes(Packet250CustomPayload packet) throws IOException {

		DataInputStream inputStream1 = new DataInputStream(new ByteArrayInputStream(packet.data));
		inputStream1.skipBytes(1);
		// how many total chunks in this packet
		byte chunkLength = inputStream1.readByte();

		// the index of this chunk
		byte chunkIndex = inputStream1.readByte();

		// the id for the combined packet
		byte incomingPacketId = inputStream1.readByte();

		// if it's not in our stack, lets create a new one
		if (!packetStack.containsKey(incomingPacketId)) {
			packetStack.put(incomingPacketId, new byte[chunkLength][]);
		}

		// the current stack
		byte[][] stack = packetStack.get(incomingPacketId);

		byte[] remainingBytes = new byte[packet.data.length - 4];
		inputStream1.read(remainingBytes, 0, remainingBytes.length);
		stack[chunkIndex] = remainingBytes;

		// count how many chunks are still null
		byte chunksLeft = 0;
		for (byte[] s : stack) {
			if (s == null) {
				chunksLeft++;
			}
		}

		// if we've got all the chunks
		if (chunksLeft == 0) {

			int totalLength = 0;
			for (byte[] s : stack) {
				totalLength += s.length;
			}

			// merge them into a single full byte array
			byte[] fullPacket = new byte[totalLength];
			int offset = 0;
			for (short i = 0; i < chunkLength; i++) {
				byte[] chunkPart = stack[i];
				System.arraycopy(chunkPart, 0, fullPacket, offset, chunkPart.length);
				offset += chunkPart.length;
			}

			// remove the entry
			packetStack.remove(incomingPacketId);

			// return the bytes
			return fullPacket;

		}
		return null;
	}
}