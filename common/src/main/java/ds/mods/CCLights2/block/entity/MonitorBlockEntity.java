package ds.mods.CCLights2.block.entity;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.block.HorizontalEntityBlock;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.utils.MonitorLocks;
import ds.mods.CCLights2.utils.MonitorMath;
import ds.mods.CCLights2.utils.ScreenCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Single-block monitor (port of TileEntityMonitor). Base class of the external monitor and the
 * tablet transceiver. Its screen is a {@link Monitor} that adjacent GPUs draw to; the screen contents
 * travel in the update tag so a player arriving later sees what is on it, and are saved with the
 * world when {@link Config#persistMonitorContents} is on. Does not tick.
 */
public class MonitorBlockEntity extends BlockEntity {
	/** Squared reach for the monitor GUI. */
	public static final double INTERACT_RANGE_SQ = 64.0D;
	/**
	 * Largest screen PNG put into update tags (they travel inside chunk packets). Larger screens get
	 * their pixels from the GPU sync instead.
	 */
	protected static final int MAX_CLIENT_PNG_BYTES = 512 * 1024;

	protected static final String TAG_SCREEN = "screen";
	protected static final String TAG_SCREEN_W = "screenW";
	protected static final String TAG_SCREEN_H = "screenH";

	/** The screen; subclasses may create it lazily (external monitor origin) or share it. */
	@Nullable
	protected Monitor mon;
	/** PNG of the screen as last encoded, reused while the texture version does not change. */
	private byte[] cachedPng;
	private int cachedPngVersion = -1;
	private Monitor cachedPngMonitor;

	public MonitorBlockEntity(BlockPos pos, BlockState state) {
		this(Registration.MONITOR_BE.get(), pos, state);
		mon = new Monitor(Config.monitorWidth, Config.monitorHeight, createMonitorObject());
	}

	/** For subclasses; does not create a screen. */
	protected MonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ------------------------------------------------------------------ screen

	/** The screen GPUs draw to, or null when this block currently has none (e.g. client before the first update tag). */
	@Nullable
	public Monitor getMonitor() {
		return mon;
	}

	/** The object gpu.getMonitor() hands to Lua. */
	protected Object createMonitorObject() {
		return new MonitorLuaObject(this);
	}

	/** What the Lua monitor object's getType() returns. */
	public String getMonitorTypeName() {
		return "monitor";
	}

	public int pixelWidth() {
		Monitor m = getMonitor();
		return m == null ? Config.monitorWidth : m.getWidth();
	}

	public int pixelHeight() {
		Monitor m = getMonitor();
		return m == null ? Config.monitorHeight : m.getHeight();
	}

	/** The side the screen faces (block state FACING). */
	public Direction getFacing() {
		BlockState state = getBlockState();
		return state.hasProperty(HorizontalEntityBlock.FACING) ? state.getValue(HorizontalEntityBlock.FACING) : Direction.NORTH;
	}

	/** Whether a player is close enough to use this monitor through its GUI. */
	public boolean canInteract(Player p) {
		return !isRemoved() && p.level() == level
				&& p.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= INTERACT_RANGE_SQ;
	}

	// ------------------------------------------------------------------ geometry of the screen face (overridden by the wall)

	/** Column of this block in its wall, counted towards the viewer's right. */
	protected int layoutXIndex() {
		return 0;
	}

	/** Row of this block in its wall, counted upwards. */
	protected int layoutYIndex() {
		return 0;
	}

	protected int layoutWidth() {
		return 1;
	}

	protected int layoutHeight() {
		return 1;
	}

	/**
	 * Screen pixel under a hit on this block, or null when the hit is not on the screen face.
	 * @return {x, y}
	 */
	@Nullable
	public int[] hitToPixel(BlockHitResult hit) {
		Direction facing = getFacing();
		if (hit.getDirection() != facing) return null;
		Vec3 loc = hit.getLocation();
		double lx = loc.x - worldPosition.getX();
		double ly = loc.y - worldPosition.getY();
		double lz = loc.z - worldPosition.getZ();
		Direction right = facing.getCounterClockWise();
		double along = MonitorMath.alongRight(lx, lz, right.getStepX(), right.getStepZ());
		return MonitorMath.hitToPixel(layoutXIndex(), layoutYIndex(), layoutWidth(), layoutHeight(), pixelWidth(), pixelHeight(), along, ly);
	}

	// ------------------------------------------------------------------ events

	/** Raises a ComputerCraft event on the computers behind every GPU drawing to this screen. */
	public void queueEvent(String event, Object[] args) {
		Monitor m = getMonitor();
		if (m == null) return;
		for (GPU g : new ArrayList<>(m.gpus)) {
			if (g.tile != null) g.tile.queueEvent(event, args);
		}
	}

	// ------------------------------------------------------------------ interaction

	/** Right click on the block: opens the monitor screen on the client. */
	public InteractionResult onUse(Player player, InteractionHand hand, BlockHitResult hit) {
		if (player.isShiftKeyDown()) return InteractionResult.PASS;
		if (level != null && level.isClientSide) Client.openMonitorScreen(this);
		return InteractionResult.SUCCESS;
	}

	/** A neighbouring block changed. */
	public void onNeighborChanged() {
	}

	// ------------------------------------------------------------------ persistence and sync

	/** PNG of the current screen, cached per texture version. */
	@Nullable
	protected byte[] encodeScreen() {
		Monitor m = getMonitor();
		if (m == null) return null;
		int version = m.tex.getVersion();
		if (cachedPng != null && cachedPngMonitor == m && cachedPngVersion == version) return cachedPng;
		byte[] png = ScreenCodec.encodePng(m.tex);
		if (png != null) {
			cachedPng = png;
			cachedPngVersion = version;
			cachedPngMonitor = m;
		}
		return png;
	}

	/** Draws PNG bytes into the screen; {@code resizeToImage} first makes the screen the image's size. */
	protected void decodeScreen(Monitor m, @Nullable byte[] png, boolean resizeToImage) {
		BufferedImage img = ScreenCodec.decode(png);
		if (img == null) return;
		MonitorLocks.withGpuLocks(m, () -> {
			if (resizeToImage) m.resize(img.getWidth(), img.getHeight(), false);
			ScreenCodec.copyInto(m.tex, img);
		});
	}

	/**
	 * Writes the screen size and, if persistence is on, its pixels.
	 * @param forClient true for update tags: the PNG is left out when larger than {@link #MAX_CLIENT_PNG_BYTES}
	 */
	protected void writeScreen(CompoundTag tag, boolean forClient) {
		Monitor m = getMonitor();
		if (m == null) return;
		tag.putInt(TAG_SCREEN_W, m.getWidth());
		tag.putInt(TAG_SCREEN_H, m.getHeight());
		if (!Config.persistMonitorContents) return;
		byte[] png = encodeScreen();
		if (png != null && (!forClient || png.length <= MAX_CLIENT_PNG_BYTES)) tag.putByteArray(TAG_SCREEN, png);
	}

	protected void readScreen(CompoundTag tag) {
		Monitor m = getMonitor();
		if (m == null) return;
		if (tag.contains(TAG_SCREEN_W, Tag.TAG_INT) && tag.contains(TAG_SCREEN_H, Tag.TAG_INT)) {
			int w = tag.getInt(TAG_SCREEN_W), h = tag.getInt(TAG_SCREEN_H);
			if (w > 0 && h > 0) MonitorLocks.resize(m, w, h, false);
		}
		if (tag.contains(TAG_SCREEN, Tag.TAG_BYTE_ARRAY)) decodeScreen(m, tag.getByteArray(TAG_SCREEN), true);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		readScreen(tag);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		writeScreen(tag, false);
	}

	/** What the client needs; subclasses add their fields. */
	protected void writeClientData(CompoundTag tag) {
		writeScreen(tag, true);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		writeClientData(tag);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** Pushes the update tag to tracking clients and marks the chunk for saving (server only). */
	protected void sendUpdate() {
		if (level == null || level.isClientSide) return;
		setChanged();
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}

	/** Keeps client-only classes out of the server: loaded only when a client calls it. */
	private static final class Client {
		static void openMonitorScreen(MonitorBlockEntity be) {
			ds.mods.CCLights2.client.ClientAccess.openMonitorScreen(be);
		}
	}
}
