package ds.mods.CCLights2.block.tileentity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.CommandEnum;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.gpu.BlendComposite;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.GpuLuaApi;
import ds.mods.CCLights2.gpu.LuaArgs;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.gpu.shader.Program.Uniform;
import ds.mods.CCLights2.gpu.shader.ShaderException;
import ds.mods.CCLights2.item.ItemRAM;
import ds.mods.CCLights2.network.PacketSenders;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The GPU block: a ComputerCraft peripheral that executes draw commands on the server and
 * replicates them to clients. Connects automatically to adjacent monitors.
 */
public class TileEntityGPU extends TileEntity implements IPeripheral, GpuLuaApi.Host {
	public static final String PERIPHERAL_TYPE = "GPU";
	private static final int MONITOR_RESCAN_TICKS = 40;
	private static final int MAX_FRAME_TICKS = 100;


	public final GPU gpu;
	private final GpuLuaApi api;
	private final List<IComputerAccess> computers = new CopyOnWriteArrayList<IComputerAccess>();
	private final Map<IComputerAccess, String> mounts = new HashMap<IComputerAccess, String>();
	private final Map<String, int[]> clicks = new HashMap<String, int[]>();
	private final Map<Monitor, TileEntityMonitor> attached = new LinkedHashMap<Monitor, TileEntityMonitor>();
	private final Random random = new Random();
	/** RAM sticks that were inserted, per size, so they drop again when the block breaks. */
	public int[] addedType = new int[ItemRAM.SIZES];
	private boolean frame = false;
	private int frameTicks = 0;
	private int ticks = 0;
	private boolean syncRequested = false;
	private boolean rescanMonitors = true;

	public TileEntityGPU() {
		gpu = new GPU(Config.gpuBaseMemory);
		gpu.tile = this;
		api = new GpuLuaApi(gpu, this);
	}

	// ------------------------------------------------------------------ mouse events

	public void startClick(EntityPlayer player, int button, int x, int y) {
		int id;
		do {
			id = random.nextInt(Integer.MAX_VALUE);
		} while (clickIdInUse(id));
		clicks.put(player.getCommandSenderName(), new int[] { button, x, y, id });
		queueEvent("monitor_down", new Object[] { button, x, y, id });
	}

	private boolean clickIdInUse(int id) {
		for (int[] c : clicks.values()) if (c[3] == id) return true;
		return false;
	}

	public void moveClick(EntityPlayer player, int nx, int ny) {
		int[] c = clicks.get(player.getCommandSenderName());
		if (c == null) return;
		c[1] = nx;
		c[2] = ny;
		queueEvent("monitor_move", new Object[] { c[0], nx, ny, c[3] });
	}

	public void endClick(EntityPlayer player) {
		int[] c = clicks.remove(player.getCommandSenderName());
		if (c == null) return;
		queueEvent("monitor_up", new Object[] { c[0], c[1], c[2], c[3] });
	}

	public void queueEvent(String event, Object[] args) {
		for (IComputerAccess c : computers) {
			Object[] full = Arrays.copyOf(args, args.length + 1);
			full[args.length] = c.getAttachmentName();
			c.queueEvent(event, full);
		}
	}

	public List<IComputerAccess> computers() {
		return computers;
	}

	// ------------------------------------------------------------------ peripheral

	@Override
	public String getType() {
		return PERIPHERAL_TYPE;
	}

	@Override
	public String[] getMethodNames() {
		return GpuLuaApi.METHOD_NAMES;
	}

	@Override
	public void attach(IComputerAccess computer) {
		computers.add(computer);
		IMount mount = ComputerCraftAPI.createResourceMount(CCLights2.class, "cclights", "lua");
		if (mount != null) {
			String location = computer.mount("cclights2", mount);
			if (location != null) mounts.put(computer, location);
		}
	}

	@Override
	public void detach(IComputerAccess computer) {
		computers.remove(computer);
		String location = mounts.remove(computer);
		if (location != null) computer.unmount(location);
	}

	@Override
	public boolean equals(IPeripheral other) {
		return other == this;
	}

	@Override
	public Object[] callMethod(final IComputerAccess computer, ILuaContext context, int method, Object[] args) throws LuaException {
		try {
			return api.call(method, args, new GpuLuaApi.ImportSource() {
				@Override
				public byte[] read(String name, String fileName) throws LuaException {
					return readImportFile(name, fileName, computer.getID());
				}
			});
		} catch (LuaException e) {
			throw e;
		} catch (RuntimeException e) {
			String name = method >= 0 && method < GpuLuaApi.METHOD_NAMES.length ? GpuLuaApi.METHOD_NAMES[method] : "?";
			CCLights2.logger.warn("GPU method " + name + " failed", e);
			throw new LuaException(name + ": " + e);
		}
	}

	@Override
	public void setFrame(boolean on) {
		frame = on;
		frameTicks = 0;
	}

	/** Reads a file from the calling computer's save folder for gpu.import("file"). */
	private byte[] readImportFile(String name, String s, int computerId) throws LuaException {
		File dir = new File(CCLights2.proxy.getWorldDir(worldObj), "computer" + File.separator + computerId);
		File f = new File(dir, s);
		try {
			if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) throw new LuaException(name + ": invalid file name");
			if (!f.isFile()) throw new LuaException(name + ": no such file '" + s + "' in the computer's folder");
			if (f.length() > 16L * 1024 * 1024) throw new LuaException(name + ": file too large");
			return Files.readAllBytes(f.toPath());
		} catch (IOException e) {
			throw new LuaException(name + ": " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void updateEntity() {
		if (worldObj.isRemote) {
			if (!syncRequested) {
				syncRequested = true;
				gpu.server = false;
				PacketSenders.requestGPUSync(this);
			}
			if (rescanMonitors || ticks % MONITOR_RESCAN_TICKS == 0) {
				rescanMonitors = false;
				synchronized (gpu) {
					connectToMonitors();
				}
			}
			ticks++;
			return;
		}
		synchronized (gpu) {
			if (rescanMonitors || ticks % MONITOR_RESCAN_TICKS == 0) {
				rescanMonitors = false;
				connectToMonitors();
			}
			if (frame && ++frameTicks > MAX_FRAME_TICKS) frame = false;
			if (!frame && !gpu.drawlist.isEmpty()) {
				PacketSenders.sendDrawList(gpu.drawlist, this);
				gpu.drawlist.clear();
			}
		}
		ticks++;
	}

	/** Called by the block when a neighbour changes so monitors get (dis)connected promptly. */
	public void onNeighbourChanged() {
		rescanMonitors = true;
	}

	/** Keeps the GPU's monitor list equal to the set of adjacent monitor blocks. */
	private void connectToMonitors() {
		// Drop monitors whose tile went away or whose screen object was rebuilt (multi-block resize).
		List<Monitor> stale = new ArrayList<Monitor>();
		for (Map.Entry<Monitor, TileEntityMonitor> e : attached.entrySet()) {
			TileEntityMonitor tile = e.getValue();
			Monitor m = e.getKey();
			if (tile.isInvalid() || tile.getMonitor() != m || !isAdjacent(tile) || !m.gpus.contains(gpu)) stale.add(m);
		}
		for (Monitor m : stale) {
			attached.remove(m);
			m.removeGPU(gpu);
		}
		boolean added = false;
		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			TileEntity te = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			if (!(te instanceof TileEntityMonitor)) continue;
			TileEntityMonitor tile = (TileEntityMonitor) te;
			Monitor mon = tile.getMonitor();
			if (mon == null || attached.containsKey(mon)) continue;
			attached.put(mon, tile);
			mon.addGPU(gpu);
			added = true;
		}
		// A client that just gained a screen needs the server's current pixels for it.
		if (added && worldObj.isRemote && syncRequested) PacketSenders.requestGPUSync(this);
	}

	private boolean isAdjacent(TileEntity tile) {
		int dx = Math.abs(tile.xCoord - xCoord), dy = Math.abs(tile.yCoord - yCoord), dz = Math.abs(tile.zCoord - zCoord);
		return dx + dy + dz == 1 && tile.getWorldObj() == worldObj;
	}

	@Override
	public void invalidate() {
		super.invalidate();
		for (Monitor m : new ArrayList<Monitor>(attached.keySet())) m.removeGPU(gpu);
		attached.clear();
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		for (Monitor m : new ArrayList<Monitor>(attached.keySet())) m.removeGPU(gpu);
		attached.clear();
	}

	// ------------------------------------------------------------------ persistence

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setIntArray("addedTypes", addedType);
		nbt.setInteger("vram", gpu.maxmem);
		synchronized (gpu) {
			nbt.setInteger("bindedSlot", gpu.bindedSlot);
			nbt.setInteger("color", gpu.state.color.getRGB());
			if (Config.persistMonitorContents) {
				NBTTagCompound textures = new NBTTagCompound();
				for (int id = 1; id < gpu.textures.length; id++) {
					Texture t = gpu.textures[id];
					if (t == null) continue;
					try {
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						ImageIO.write(t.getImage(), "png", out);
						textures.setByteArray(String.valueOf(id), out.toByteArray());
					} catch (IOException e) {
						CCLights2.logger.warn("Failed to save GPU texture " + id + ": " + e);
					}
				}
				nbt.setTag("textures", textures);
			}
			NBTTagCompound shaders = new NBTTagCompound();
			for (int id = 1; id < gpu.shaders.length; id++) {
				ShaderInstance sh = gpu.shaders[id];
				if (sh == null) continue;
				NBTTagCompound tag = new NBTTagCompound();
				tag.setString("source", sh.source);
				int[] bits = new int[sh.values.length];
				for (int i = 0; i < bits.length; i++) bits[i] = Float.floatToIntBits(sh.values[i]);
				tag.setIntArray("values", bits);
				tag.setIntArray("samplers", sh.samplers);
				shaders.setTag(String.valueOf(id), tag);
			}
			nbt.setTag("shaders", shaders);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		int[] added = nbt.getIntArray("addedTypes");
		addedType = new int[ItemRAM.SIZES];
		if (added != null) System.arraycopy(added, 0, addedType, 0, Math.min(added.length, addedType.length));
		int mem = Config.gpuBaseMemory;
		for (int i = 0; i < addedType.length; i++) mem += addedType[i] * (i + 1) * Config.gpuRamPerStick;
		gpu.maxmem = Math.max(mem, nbt.getInteger("vram"));
		synchronized (gpu) {
			if (nbt.hasKey("textures")) {
				NBTTagCompound textures = nbt.getCompoundTag("textures");
				@SuppressWarnings("unchecked")
				java.util.Set<String> keys = textures.func_150296_c();
				for (String key : keys) {
					try {
						int id = Integer.parseInt(key);
						BufferedImage img = ImageIO.read(new ByteArrayInputStream(textures.getByteArray(key)));
						if (img == null || id <= 0 || id >= GPU.MAX_TEXTURES) continue;
						Texture t = new Texture(img.getWidth(), img.getHeight());
						t.getGraphics().drawImage(img, 0, 0, null);
						t.markDirty();
						gpu.textures[id] = t;
					} catch (Exception e) {
						CCLights2.logger.warn("Failed to load GPU texture " + key + ": " + e);
					}
				}
			}
			if (nbt.hasKey("shaders")) {
				NBTTagCompound shaders = nbt.getCompoundTag("shaders");
				@SuppressWarnings("unchecked")
				java.util.Set<String> keys = shaders.func_150296_c();
				for (String key : keys) {
					try {
						int id = Integer.parseInt(key);
						NBTTagCompound tag = shaders.getCompoundTag(key);
						int slot = gpu.createShader(tag.getString("source"), id);
						ShaderInstance sh = gpu.shaders[slot];
						int[] bits = tag.getIntArray("values");
						for (int i = 0; i < bits.length && i < sh.values.length; i++) sh.values[i] = Float.intBitsToFloat(bits[i]);
						int[] samplers = tag.getIntArray("samplers");
						System.arraycopy(samplers, 0, sh.samplers, 0, Math.min(samplers.length, sh.samplers.length));
					} catch (Exception e) {
						CCLights2.logger.warn("Failed to restore shader " + key + ": " + e.getMessage());
					}
				}
			}
			if (nbt.hasKey("color")) gpu.state.color = new java.awt.Color(nbt.getInteger("color"), true);
			int slot = nbt.getInteger("bindedSlot");
			if (slot > 0 && slot < GPU.MAX_TEXTURES && gpu.textures[slot] != null) {
				gpu.bindedTexture = gpu.textures[slot];
				gpu.bindedSlot = slot;
			}
		}
	}
}
