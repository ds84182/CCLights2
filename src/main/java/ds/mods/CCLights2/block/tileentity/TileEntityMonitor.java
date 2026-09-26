package ds.mods.CCLights2.block.tileentity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * A single-block monitor. Its screen is a {@link Monitor} that adjacent GPUs draw to.
 * The screen contents travel with the tile's description packet so a player who arrives later
 * sees what is currently on it, and are saved with the world.
 */
public class TileEntityMonitor extends TileEntity {
	protected Monitor mon;
	/** PNG of the screen as last encoded, reused while the texture version does not change. */
	private byte[] cachedPng;
	private int cachedPngVersion = -1;

	public TileEntityMonitor() {
		this(true);
	}

	protected TileEntityMonitor(boolean createMonitor) {
		if (createMonitor) mon = new Monitor(Config.monitorWidth, Config.monitorHeight, getMonitorObject());
	}

	/** The screen GPUs draw to, or null when this tile currently has none. */
	public Monitor getMonitor() {
		return mon;
	}

	/** Called by GUIs and the tablet: the texture to show on screen. */
	public Texture getScreenTexture() {
		Monitor m = getMonitor();
		return m == null ? null : m.tex;
	}

	public ILuaObject getMonitorObject() {
		return new MonitorObject();
	}

	/** Whether a player is close enough to interact with this monitor through the GUI. */
	public boolean canInteract(EntityPlayer player) {
		return player.worldObj == worldObj && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0D;
	}

	@Override
	public boolean canUpdate() {
		return false;
	}

	// ------------------------------------------------------------------ sync and persistence

	protected byte[] encodeScreen() {
		Monitor m = getMonitor();
		if (m == null) return null;
		Texture tex = m.tex;
		if (cachedPng != null && cachedPngVersion == tex.getVersion()) return cachedPng;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(tex.getImage(), "png", out);
			cachedPng = out.toByteArray();
			cachedPngVersion = tex.getVersion();
			return cachedPng;
		} catch (IOException e) {
			CCLights2.logger.warn("Failed to encode monitor contents: " + e);
			return null;
		}
	}

	protected void decodeScreen(byte[] png) {
		Monitor m = getMonitor();
		if (m == null || png == null || png.length == 0) return;
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
			if (img == null) return;
			m.resize(img.getWidth(), img.getHeight(), false);
			m.tex.getGraphics().drawImage(img, 0, 0, null);
			m.tex.markDirty();
			m.tex.texUpdate();
		} catch (IOException e) {
			CCLights2.logger.warn("Failed to decode monitor contents: " + e);
		}
	}

	protected void writeScreen(NBTTagCompound nbt) {
		Monitor m = getMonitor();
		if (m == null) return;
		nbt.setInteger("screenW", m.getWidth());
		nbt.setInteger("screenH", m.getHeight());
		if (Config.persistMonitorContents) {
			byte[] png = encodeScreen();
			if (png != null) nbt.setByteArray("screen", png);
		}
	}

	protected void readScreen(NBTTagCompound nbt) {
		Monitor m = getMonitor();
		if (m == null) return;
		if (nbt.hasKey("screenW") && nbt.hasKey("screenH")) {
			m.resize(nbt.getInteger("screenW"), nbt.getInteger("screenH"), false);
		}
		if (nbt.hasKey("screen")) decodeScreen(nbt.getByteArray("screen"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		writeScreen(nbt);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		readScreen(nbt);
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		readFromNBT(pkt.func_148857_g());
	}

	/** The object gpu.getMonitor() hands to Lua. */
	public class MonitorObject implements ILuaObject {
		@Override
		public String[] getMethodNames() {
			return new String[] { "getResolution", "getSize", "getType", "getPosition" };
		}

		@Override
		public Object[] callMethod(ILuaContext context, int method, Object[] arguments) throws LuaException {
			Monitor m = getMonitor();
			switch (method) {
			case 0:
			case 1:
				if (m == null) return null;
				return new Object[] { m.getWidth(), m.getHeight() };
			case 2:
				return new Object[] { monitorTypeName() };
			case 3:
				return new Object[] { xCoord, yCoord, zCoord };
			default:
				return null;
			}
		}
	}

	protected String monitorTypeName() {
		return "monitor";
	}
}
