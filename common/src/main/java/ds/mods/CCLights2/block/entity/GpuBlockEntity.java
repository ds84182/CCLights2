package ds.mods.CCLights2.block.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.GpuHost;
import ds.mods.CCLights2.gpu.GpuLuaApi;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.ShaderInstance;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.item.RamItem;
import ds.mods.CCLights2.network.PacketSenders;
import ds.mods.CCLights2.utils.ImportFiles;
import ds.mods.CCLights2.utils.ScreenCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The GPU block entity (port of TileEntityGPU). Owns the {@link GPU} and is its {@link GpuHost}; the
 * CC: Tweaked side lives in {@link GpuPeripheral} (a BlockEntity cannot implement IPeripheral itself:
 * BlockEntity#getType() and IPeripheral#getType() clash).
 * <p>
 * The server executes draw commands (on the ComputerCraft thread, under the GPU lock) and flushes the
 * replicated ones to clients every tick; clients keep a replica GPU that draws into the same adjacent
 * monitors. Connects automatically to adjacent monitors on both sides.
 */
public class GpuBlockEntity extends BlockEntity implements GpuHost, GpuLuaApi.Host {
	private static final int MONITOR_RESCAN_TICKS = 40;
	/** A frame (startFrame without endFrame) is flushed anyway after this many ticks. */
	private static final int MAX_FRAME_TICKS = 100;

	private final GPU gpu;
	private final GpuLuaApi api;
	private final GpuPeripheral peripheral = new GpuPeripheral(this);
	private final AttachedComputerSet computers = new AttachedComputerSet();
	private final Map<IComputerAccess, String> mounts = new ConcurrentHashMap<>();
	/** Open clicks per player: {button, x, y, clickId}. Server thread. */
	private final Map<UUID, int[]> clicks = new HashMap<>();
	/** Monitors this GPU draws to and the block each came from. Guarded by the GPU lock. */
	private final Map<Monitor, MonitorBlockEntity> attached = new LinkedHashMap<>();
	/** Players waiting for a full GPU sync. Server thread. */
	private final Set<ServerPlayer> pendingSyncs = new LinkedHashSet<>();
	private final Random random = new Random();
	/** Installed RAM sticks per size (index into RamItem.SIZES), so they drop again when the block breaks. */
	private final int[] installedRam = new int[RamItem.SIZES.length];

	private volatile boolean frame = false;
	private volatile int frameTicks = 0;
	private volatile boolean rescanMonitors = true;
	/**
	 * Side of this block the current screen (texture slot 0) is on. Saved in NBT and sent to clients in
	 * the update tag, so the screen a program draws to survives reloads and every client agrees with the
	 * server on which of several adjacent screens is the current one. Guarded by the GPU lock.
	 */
	@Nullable
	private Direction currentSide;
	/** Server: after a load, prefer the saved side once its screen is connected instead of "last connected". */
	private boolean honourSavedSide;
	private int ticks = 0;
	/** Client: set when this replica needs the server's full state (first tick, new monitor). */
	public volatile boolean needsClientSync = true;
	/** Client: false until the first snapshot arrived; draw lists before that would replay against an empty GPU. */
	public volatile boolean clientSynced = false;
	/** Client: the server answers at most one sync request per GPU per half second; stay under that. */
	private static final long SYNC_REQUEST_INTERVAL_NANOS = 600_000_000L;
	private long lastSyncRequestNanos = Long.MIN_VALUE / 2;

	public GpuBlockEntity(BlockPos pos, BlockState state) {
		super(Registration.GPU_BE.get(), pos, state);
		gpu = new GPU(Config.gpuBaseMemory);
		gpu.tile = this;
		api = new GpuLuaApi(gpu, this);
	}

	/** The CC: Tweaked peripheral for this block (one instance per block entity); loader modules expose it on every side. */
	public IPeripheral peripheral() {
		return peripheral;
	}

	public GPU getGpu() {
		return gpu;
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		gpu.server = !level.isClientSide;
	}

	// ------------------------------------------------------------------ mouse events

	/** A player pressed a mouse button on a screen this GPU draws to: queues monitor_down. Server thread. */
	public void startClick(Player p, int button, int x, int y) {
		int id;
		do {
			id = random.nextInt(Integer.MAX_VALUE);
		} while (clickIdInUse(id));
		clicks.put(p.getUUID(), new int[] { button, x, y, id });
		queueEvent("monitor_down", new Object[] { button, x, y, id });
	}

	private boolean clickIdInUse(int id) {
		for (int[] c : clicks.values()) if (c[3] == id) return true;
		return false;
	}

	/** The player's pressed pointer moved: queues monitor_move. */
	public void moveClick(Player p, int x, int y) {
		int[] c = clicks.get(p.getUUID());
		if (c == null) return;
		c[1] = x;
		c[2] = y;
		queueEvent("monitor_move", new Object[] { c[0], x, y, c[3] });
	}

	/** The player released the button: queues monitor_up at the last position. */
	public void endClick(Player p) {
		int[] c = clicks.remove(p.getUUID());
		if (c == null) return;
		queueEvent("monitor_up", new Object[] { c[0], c[1], c[2], c[3] });
	}

	/** Queues an event on every attached computer, appending that computer's attachment name. */
	@Override
	public void queueEvent(String event, Object[] args) {
		Object[] base = args == null ? new Object[0] : args;
		computers.forEach(c -> {
			Object[] full = Arrays.copyOf(base, base.length + 1);
			full[base.length] = c.getAttachmentName();
			c.queueEvent(event, full);
		});
	}

	@Override
	public void markDirty() {
		setChanged();
	}

	// ------------------------------------------------------------------ peripheral

	/** Called by {@link GpuPeripheral#attach}: mounts the Lua programs at /cclights2. */
	public void attach(IComputerAccess computer) {
		computers.add(computer);
		MinecraftServer server = level == null ? null : level.getServer();
		if (server == null) return;
		try {
			Mount mount = ComputerCraftAPI.createResourceMount(server, CCLights2.MOD_ID, "lua");
			if (mount == null) return;
			String location = computer.mount("cclights2", mount);
			if (location != null) mounts.put(computer, location);
		} catch (RuntimeException e) {
			CCLights2.LOGGER.warn("Could not mount the CCLights2 programs on computer {}", computer.getID(), e);
		}
	}

	/** Called by {@link GpuPeripheral#detach}. */
	public void detach(IComputerAccess computer) {
		String location = mounts.remove(computer);
		if (location != null) {
			try {
				computer.unmount(location);
			} catch (RuntimeException ignored) {
				// already gone
			}
		}
		computers.remove(computer);
	}

	/** Runs Lua method {@code method} (index into GpuLuaApi.METHOD_NAMES). ComputerCraft thread. */
	@Nullable
	Object[] callLua(IComputerAccess computer, int method, Object[] args) throws LuaException {
		try {
			return api.call(method, args, (name, file) -> readImportFile(name, file, computer.getID()));
		} catch (LuaException e) {
			throw e;
		} catch (RuntimeException e) {
			String name = method >= 0 && method < GpuLuaApi.METHOD_NAMES.length ? GpuLuaApi.METHOD_NAMES[method] : "?";
			CCLights2.LOGGER.warn("GPU method {} failed", name, e);
			throw new LuaException(name + ": " + e);
		}
	}

	/** Frame batching: while on, replicated commands are held back until endFrame (or a timeout). */
	@Override
	public void setFrame(boolean on) {
		frame = on;
		frameTicks = 0;
	}

	/** Reads a file from the calling computer's save folder for gpu.import("file"). */
	private byte[] readImportFile(String method, String file, int computerId) throws LuaException {
		MinecraftServer server = level == null ? null : level.getServer();
		if (server == null) throw new LuaException(method + ": file import is not available here");
		return ImportFiles.read(ImportFiles.computerDir(server.getWorldPath(LevelResource.ROOT), computerId), method, file);
	}

	// ------------------------------------------------------------------ ticking

	/** Server ticker installed by GpuBlock#getTicker: monitor discovery, draw list flush, sync answers. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, GpuBlockEntity be) {
		be.tickServer();
	}

	/** Client ticker installed by GpuBlock#getTicker: monitor discovery and the initial sync request. */
	public static void clientTick(Level level, BlockPos pos, BlockState state, GpuBlockEntity be) {
		be.tickClient();
	}

	private void tickServer() {
		synchronized (gpu) {
			if (rescanMonitors || ticks % MONITOR_RESCAN_TICKS == 0) {
				rescanMonitors = false;
				connectToMonitors();
			}
			if (frame && ++frameTicks > MAX_FRAME_TICKS) frame = false;
			if (!frame && !gpu.drawlist.isEmpty()) {
				List<DrawCMD> batch = new ArrayList<>(gpu.drawlist);
				gpu.drawlist.clear();
				PacketSenders.sendDrawList(this, batch);
			}
			// A sync snapshot must not include commands that are still waiting to be flushed, or the
			// client would apply them twice; answer once the draw list is empty.
			if (gpu.drawlist.isEmpty() && !pendingSyncs.isEmpty()) {
				for (ServerPlayer p : pendingSyncs) {
					if (!p.hasDisconnected()) PacketSenders.sendGpuSync(this, p);
				}
				pendingSyncs.clear();
			}
		}
		ticks++;
	}

	private void tickClient() {
		if (rescanMonitors || ticks % MONITOR_RESCAN_TICKS == 0) {
			rescanMonitors = false;
			synchronized (gpu) {
				connectToMonitors();
			}
		}
		if (needsClientSync) {
			long now = System.nanoTime();
			if (now - lastSyncRequestNanos >= SYNC_REQUEST_INTERVAL_NANOS) {
				needsClientSync = false;
				lastSyncRequestNanos = now;
				requestSync();
			}
		}
		ticks++;
	}

	/** Client: asks the server for this GPU's full state. */
	public void requestSync() {
		PacketSenders.requestGpuSync(this);
	}

	/**
	 * Server: a client asked for this GPU's full state; it is sent from the next tick (after pending
	 * draw commands were flushed) through PacketSenders.sendGpuSync.
	 */
	public void queueSync(ServerPlayer player) {
		pendingSyncs.add(player);
	}

	/** Called by the block when a neighbour changes so monitors get (dis)connected promptly. */
	public void onNeighborChanged() {
		rescanMonitors = true;
	}

	/** The block of the screen this GPU currently draws to (else the first attached), or null. */
	@Nullable
	public MonitorBlockEntity getAttachedMonitor() {
		synchronized (gpu) {
			MonitorBlockEntity current = gpu.currentMonitor == null ? null : attached.get(gpu.currentMonitor);
			if (current != null) return current;
			return attached.isEmpty() ? null : attached.values().iterator().next();
		}
	}

	/** Keeps the GPU's monitor list equal to the set of adjacent monitor screens. Call under the GPU lock. */
	private void connectToMonitors() {
		if (level == null) return;
		List<Monitor> stale = new ArrayList<>();
		for (Map.Entry<Monitor, MonitorBlockEntity> e : attached.entrySet()) {
			MonitorBlockEntity tile = e.getValue();
			Monitor m = e.getKey();
			if (tile.isRemoved() || tile.getLevel() != level || tile.getMonitor() != m || !isAdjacent(tile) || !m.gpus.contains(gpu)) stale.add(m);
		}
		for (Monitor m : stale) {
			attached.remove(m);
			m.removeGPU(gpu);
		}
		boolean added = false;
		for (Direction d : Direction.values()) {
			BlockPos p = worldPosition.relative(d);
			if (!level.isLoaded(p)) continue;
			if (!(level.getBlockEntity(p) instanceof MonitorBlockEntity tile) || tile.isRemoved()) continue;
			Monitor mon = tile.getMonitor();
			if (mon == null || attached.containsKey(mon)) continue;
			attached.put(mon, tile);
			mon.addGPU(gpu);
			added = true;
		}
		applyCurrentSide();
		// A client that just gained a screen needs the server's current pixels for it.
		if (added && level.isClientSide) needsClientSync = true;
	}

	/**
	 * Keeps {@link #currentSide} and the GPU's current monitor in agreement. Clients always follow the
	 * side the server sent; the server follows the saved side right after a load and otherwise records
	 * which side the (last connected) current screen is on and pushes it to the clients. Under the GPU lock.
	 */
	private void applyCurrentSide() {
		if (level == null) return;
		boolean client = level.isClientSide;
		if (currentSide != null && (client || honourSavedSide)) {
			BlockPos p = worldPosition.relative(currentSide);
			if (!level.isLoaded(p)) return; // wait until that neighbour is in
			Monitor wanted = level.getBlockEntity(p) instanceof MonitorBlockEntity tile ? tile.getMonitor() : null;
			if (wanted != null && attached.containsKey(wanted)) {
				if (gpu.currentMonitor != wanted) gpu.setMonitor(wanted);
				honourSavedSide = false;
				if (client) needsClientSync = true;
				return;
			}
			if (client) return;
			// nothing (left) on the saved side: fall back to the usual rule
			if (wanted == null) honourSavedSide = false;
			else return;
		}
		if (client) return;
		MonitorBlockEntity cur = gpu.currentMonitor == null ? null : attached.get(gpu.currentMonitor);
		Direction side = cur == null ? null : directionTo(cur.getBlockPos());
		if (side != currentSide) {
			currentSide = side;
			setChanged();
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	@Nullable
	private Direction directionTo(BlockPos other) {
		for (Direction d : Direction.values()) if (worldPosition.relative(d).equals(other)) return d;
		return null;
	}

	private boolean isAdjacent(BlockEntity tile) {
		return tile.getBlockPos().distManhattan(worldPosition) == 1;
	}

	private void disconnectMonitors() {
		synchronized (gpu) {
			for (Monitor m : new ArrayList<>(attached.keySet())) m.removeGPU(gpu);
			attached.clear();
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		disconnectMonitors();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		rescanMonitors = true;
	}

	// ------------------------------------------------------------------ RAM

	/** Installs one RAM stick (server). */
	public void installRam(RamItem ram) {
		int idx = RamItem.sizeIndex(ram.getKilobytes());
		if (idx < 0) return;
		installedRam[idx]++;
		synchronized (gpu) {
			gpu.maxmem += ram.getMemory();
		}
		setChanged();
	}

	/** The RAM sticks installed in this GPU, as item stacks to drop when the block breaks. */
	public List<ItemStack> getRamDrops() {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < installedRam.length; i++) {
			RamItem item = RamItem.bySizeIndex(i);
			int n = installedRam[i];
			while (item != null && n > 0) {
				int count = Math.min(item.getMaxStackSize(), n);
				n -= count;
				out.add(new ItemStack(item, count));
			}
		}
		return out;
	}

	/** Right click: inserting a RAM stick adds memory (the 1.7.10 BlockGPU.onBlockActivated behaviour). */
	public InteractionResult onUse(Player player, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof RamItem ram) || RamItem.sizeIndex(ram.getKilobytes()) < 0) return InteractionResult.PASS;
		if (level == null || level.isClientSide) return InteractionResult.SUCCESS;
		if (!player.getAbilities().instabuild) held.shrink(1);
		installRam(ram);
		player.displayClientMessage(Component.translatable("chat.cclights.gpu.ram_added", ram.getKilobytes(), gpu.maxmem), false);
		return InteractionResult.CONSUME;
	}

	// ------------------------------------------------------------------ persistence

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putIntArray("installedRam", installedRam.clone());
		synchronized (gpu) {
			tag.putInt("vram", gpu.maxmem);
			if (currentSide != null) tag.putString("currentSide", currentSide.getName());
			tag.putInt("bindedSlot", gpu.bindedSlot);
			tag.putInt("color", gpu.state.color.getRGB());
			if (Config.persistMonitorContents) {
				CompoundTag textures = new CompoundTag();
				for (int id = 1; id < gpu.textures.length; id++) {
					Texture t = gpu.textures[id];
					if (t == null) continue;
					byte[] png = ScreenCodec.encodePng(t);
					if (png != null) textures.putByteArray(String.valueOf(id), png);
					else CCLights2.LOGGER.warn("Failed to save GPU texture {} at {}", id, worldPosition);
				}
				tag.put("textures", textures);
			}
			CompoundTag shaders = new CompoundTag();
			for (int id = 1; id < gpu.shaders.length; id++) {
				ShaderInstance sh = gpu.shaders[id];
				if (sh == null) continue;
				CompoundTag s = new CompoundTag();
				s.putString("source", sh.source);
				int[] bits = new int[sh.values.length];
				for (int i = 0; i < bits.length; i++) bits[i] = Float.floatToIntBits(sh.values[i]);
				s.putIntArray("values", bits);
				s.putIntArray("samplers", sh.samplers.clone());
				shaders.put(String.valueOf(id), s);
			}
			tag.put("shaders", shaders);
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains("installedRam", Tag.TAG_INT_ARRAY)) {
			int[] saved = tag.getIntArray("installedRam");
			Arrays.fill(installedRam, 0);
			System.arraycopy(saved, 0, installedRam, 0, Math.min(saved.length, installedRam.length));
		}
		int mem = Config.gpuBaseMemory;
		for (int i = 0; i < installedRam.length; i++) mem += installedRam[i] * RamItem.SIZES[i] * Config.gpuRamPerStick;
		synchronized (gpu) {
			gpu.maxmem = Math.max(mem, tag.getInt("vram"));
			if (tag.contains("currentSide", Tag.TAG_STRING)) {
				Direction side = Direction.byName(tag.getString("currentSide"));
				if (side != currentSide) {
					currentSide = side;
					honourSavedSide = side != null;
					rescanMonitors = true;
				}
			}
			if (tag.contains("textures", Tag.TAG_COMPOUND)) {
				CompoundTag textures = tag.getCompound("textures");
				for (String key : textures.getAllKeys()) {
					try {
						int id = Integer.parseInt(key);
						if (id <= 0 || id >= GPU.MAX_TEXTURES) continue;
						Texture t = ScreenCodec.decodeTexture(textures.getByteArray(key));
						if (t == null) continue;
						if (gpu.textures[id] != null) gpu.textures[id].dispose();
						gpu.textures[id] = t;
					} catch (RuntimeException e) {
						CCLights2.LOGGER.warn("Failed to load GPU texture {}: {}", key, e.toString());
					}
				}
			}
			if (tag.contains("shaders", Tag.TAG_COMPOUND)) {
				CompoundTag shaders = tag.getCompound("shaders");
				for (String key : shaders.getAllKeys()) {
					try {
						int id = Integer.parseInt(key);
						CompoundTag s = shaders.getCompound(key);
						int slot = gpu.createShader(s.getString("source"), id);
						ShaderInstance sh = gpu.shaders[slot];
						int[] bits = s.getIntArray("values");
						for (int i = 0; i < bits.length && i < sh.values.length; i++) sh.values[i] = Float.intBitsToFloat(bits[i]);
						int[] samplers = s.getIntArray("samplers");
						System.arraycopy(samplers, 0, sh.samplers, 0, Math.min(samplers.length, sh.samplers.length));
					} catch (LuaException | RuntimeException e) {
						CCLights2.LOGGER.warn("Failed to restore shader {}: {}", key, e.getMessage());
					}
				}
			}
			if (tag.contains("color", Tag.TAG_INT)) gpu.state.color = new java.awt.Color(tag.getInt("color"), true);
			int slot = tag.getInt("bindedSlot");
			if (slot > 0 && slot < GPU.MAX_TEXTURES && gpu.textures[slot] != null) {
				gpu.bindedTexture = gpu.textures[slot];
				gpu.bindedSlot = slot;
			}
		}
	}

	/** Clients get the GPU's state through the GPU sync, not through the update tag. */
	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		synchronized (gpu) {
			tag.putInt("vram", gpu.maxmem);
			if (currentSide != null) tag.putString("currentSide", currentSide.getName());
		}
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
