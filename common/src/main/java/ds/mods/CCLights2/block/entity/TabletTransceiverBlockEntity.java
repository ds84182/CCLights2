package ds.mods.CCLights2.block.entity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.item.TabletItem;
import ds.mods.CCLights2.utils.TabMesg;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tablet transceiver (port of TileEntityTTrans): a {@value #WIDTH}x{@value #HEIGHT} monitor that tablets
 * paired with it show wirelessly, and a peripheral ({@link TabletTransceiverPeripheral}) so computers
 * get the tablets' input events.
 */
public class TabletTransceiverBlockEntity extends MonitorBlockEntity {
	public static final int WIDTH = 512;
	public static final int HEIGHT = 288;

	private final TabletTransceiverPeripheral peripheral = new TabletTransceiverPeripheral(this);
	private final AttachedComputerSet computers = new AttachedComputerSet();
	/** Ids of the paired tablets (TabletItem#getTabletId). Read from ComputerCraft threads. */
	private final List<UUID> tablets = new CopyOnWriteArrayList<>();
	private UUID id = UUID.randomUUID();
	private volatile boolean dirty = false;

	public TabletTransceiverBlockEntity(BlockPos pos, BlockState state) {
		super(Registration.TABLET_TRANSCEIVER_BE.get(), pos, state);
		mon = new Monitor(WIDTH, HEIGHT, createMonitorObject());
	}

	/** The CC: Tweaked peripheral for this block; loader modules expose it on every side. */
	public IPeripheral peripheral() {
		return peripheral;
	}

	/** Server ticker installed by TabletTransceiverBlock#getTicker: saves and syncs pairing changes. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, TabletTransceiverBlockEntity be) {
		if (be.dirty) {
			be.dirty = false;
			be.sendUpdate();
		}
	}

	/** This transceiver's id, stored in paired tablets. */
	public UUID getId() {
		return id;
	}

	@Override
	public String getMonitorTypeName() {
		return "tablet";
	}

	// ------------------------------------------------------------------ tablets

	/** Pairs a tablet (its TabletItem#getTabletId) with this transceiver. Server thread. */
	public void connectTablet(UUID tablet) {
		if (!tablets.contains(tablet)) {
			tablets.add(tablet);
			dirty = true;
		}
	}

	/** Forgets every paired tablet. */
	public void disconnectAll() {
		if (!tablets.isEmpty()) {
			tablets.clear();
			dirty = true;
		}
	}

	public boolean isPaired(UUID tablet) {
		return tablets.contains(tablet);
	}

	public int getTabletCount() {
		return tablets.size();
	}

	/** The i-th paired tablet (0-based), or null. */
	@Nullable
	public UUID getTablet(int index) {
		List<UUID> snapshot = tablets;
		return index >= 0 && index < snapshot.size() ? snapshot.get(index) : null;
	}

	/** Tablets work within {@link Config#tabletRange} blocks instead of the usual GUI reach. */
	@Override
	public boolean canInteract(Player p) {
		double r = Config.tabletRange + 1;
		return !isRemoved() && p.level() == level
				&& p.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= r * r;
	}

	// ------------------------------------------------------------------ computers

	public void attach(IComputerAccess computer) {
		computers.add(computer);
	}

	public void detach(IComputerAccess computer) {
		computers.remove(computer);
	}

	/**
	 * Raises an event on the computers attached to this transceiver directly (with their attachment
	 * name appended) and on the computers behind the GPUs drawing to its screen.
	 */
	@Override
	public void queueEvent(String event, Object[] args) {
		computers.forEach(c -> {
			Object[] full = Arrays.copyOf(args, args.length + 1);
			full[args.length] = c.getAttachmentName();
			c.queueEvent(event, full);
		});
		super.queueEvent(event, args);
	}

	// ------------------------------------------------------------------ interaction

	/** Tablets in hand pair through TabletItem#useOn; anything else opens the screen like a monitor. */
	@Override
	public InteractionResult onUse(Player player, InteractionHand hand, BlockHitResult hit) {
		if (player.getItemInHand(hand).getItem() instanceof TabletItem) return InteractionResult.PASS;
		return super.onUse(player, hand, hit);
	}

	// ------------------------------------------------------------------ persistence

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level.isClientSide) TabMesg.setPosition(id, worldPosition);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains("uuid", Tag.TAG_STRING)) {
			try {
				id = UUID.fromString(tag.getString("uuid"));
			} catch (IllegalArgumentException ignored) {
				// keep the current id
			}
		}
		if (level != null && level.isClientSide) TabMesg.setPosition(id, worldPosition);
		if (tag.contains("tablets", Tag.TAG_LIST)) {
			tablets.clear();
			ListTag list = tag.getList("tablets", Tag.TAG_STRING);
			for (int i = 0; i < list.size(); i++) {
				try {
					tablets.add(UUID.fromString(list.getString(i)));
				} catch (IllegalArgumentException ignored) {
					// skip broken entries
				}
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putString("uuid", id.toString());
		ListTag list = new ListTag();
		for (UUID t : tablets) list.add(StringTag.valueOf(t.toString()));
		tag.put("tablets", list);
	}

	@Override
	protected void writeClientData(CompoundTag tag) {
		super.writeClientData(tag);
		tag.putString("uuid", id.toString());
	}
}
