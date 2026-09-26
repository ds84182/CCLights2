package ds.mods.CCLights2.block;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.block.entity.TabletTransceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The tablet transceiver: a monitor whose screen is shown on paired tablets; also a peripheral. */
public class TabletTransceiverBlock extends HorizontalEntityBlock {
	public TabletTransceiverBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TabletTransceiverBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, Registration.TABLET_TRANSCEIVER_BE.get(), TabletTransceiverBlockEntity::serverTick);
	}

	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return level.getBlockEntity(pos) instanceof TabletTransceiverBlockEntity be ? be.onUse(player, hand, hit) : InteractionResult.PASS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if (level.getBlockEntity(pos) instanceof TabletTransceiverBlockEntity be) be.onNeighborChanged();
	}
}
