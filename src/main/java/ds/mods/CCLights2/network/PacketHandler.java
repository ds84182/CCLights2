package ds.mods.CCLights2.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import ds.mods.CCLights2.CCLights2;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Forge channel glue. Every CCLights2 packet is an opaque byte[] whose first byte is the
 * packet type; {@link PacketProcessor} decodes it on the game thread of the receiving side.
 */
public class PacketHandler implements IMessageHandler<PacketHandler.PacketMessage, IMessage> {

	@Override
	public IMessage onMessage(final PacketMessage message, MessageContext ctx) {
		// Netty thread here: only grab what we need and hop to the game thread.
		final Side side = ctx.side;
		final EntityPlayer player = side == Side.SERVER ? ctx.getServerHandler().playerEntity : null;
		CCLights2.proxy.runOnGameThread(side, new Runnable() {
			@Override
			public void run() {
				PacketProcessor.handle(side, message.data, player);
			}
		});
		return null;
	}

	public static class PacketMessage implements IMessage {
		public byte[] data;

		public PacketMessage() {}

		public PacketMessage(byte[] data) {
			this.data = data;
		}

		@Override
		public void fromBytes(ByteBuf buf) {
			int len = buf.readInt();
			if (len < 0 || len > buf.readableBytes()) {
				data = new byte[0];
				return;
			}
			data = new byte[len];
			buf.readBytes(data);
		}

		@Override
		public void toBytes(ByteBuf buf) {
			buf.writeInt(data.length);
			buf.writeBytes(data);
		}
	}
}
