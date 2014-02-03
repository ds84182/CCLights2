package ds.mods.CCLights2.network;
import net.minecraft.client.Minecraft;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.Player;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;

public class OnJoin implements IConnectionHandler {
	@Override
	public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager)
    {
		   if(Config.monitorSize[0] != 256 || Config.monitorSize[1] != 144){
			   if(MinecraftServer.getServer().isDedicatedServer()){
           PacketSenders.SYNC(Config.monitorSize[0], Config.monitorSize[1],player);}
		   }
	 }
	@Override
	public void clientLoggedIn(NetHandler clientHandler,INetworkManager manager, Packet1Login login) 
	{
		if(Minecraft.getMinecraft().isSingleplayer())
		{
			CCLights2.debug("Singleplayer detected, sync not needed");
		}
		else{
		 Config.setDefaults();
		 CCLights2.debug("PREP'd for SYNC");
		}
	}
	//why can't i make this class abstract ;_;
	public String connectionReceived(NetLoginHandler netHandler,INetworkManager manager) {return null;}
	public void connectionOpened(NetHandler netClientHandler, String server,int port, INetworkManager manager) {}
	public void connectionOpened(NetHandler netClientHandler,MinecraftServer server, INetworkManager manager) {}
	public void connectionClosed(INetworkManager manager) {}
}