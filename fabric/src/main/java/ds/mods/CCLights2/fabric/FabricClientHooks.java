package ds.mods.CCLights2.fabric;

import ds.mods.CCLights2.network.NetworkKinds;
import ds.mods.CCLights2.platform.Platform;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** Client-only Fabric calls, kept out of FabricPlatform so a dedicated server never loads them. */
final class FabricClientHooks {
	private FabricClientHooks() {}

	static void registerReceiver(Platform.NetworkReceiver receiver) {
		ClientPlayNetworking.registerGlobalReceiver(NetworkKinds.CHANNEL, (client, handler, buf, responseSender) -> {
			int kind = buf.readVarInt();
			byte[] payload = buf.readByteArray();
			client.execute(() -> receiver.receive(kind, payload, null));
		});
	}

	static void send(int kind, byte[] payload) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(kind);
		buf.writeByteArray(payload);
		ClientPlayNetworking.send(NetworkKinds.CHANNEL, buf);
	}

	static void registerClientTick(Runnable r) {
		ClientTickEvents.END_CLIENT_TICK.register(client -> r.run());
	}
}
