package ds.mods.CCLights2.block;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.block.entity.ExternalMonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** One block of a multi-block external monitor wall. Clicking the face is a touch on the screen. */
public class ExternalMonitorBlock extends HorizontalEntityBlock {
	public ExternalMonitorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ExternalMonitorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, Registration.EXTERNAL_MONITOR_BE.get(), ExternalMonitorBlockEntity::serverTick);
	}

	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return level.getBlockEntity(pos) instanceof ExternalMonitorBlockEntity be ? be.onUse(player, hand, hit) : InteractionResult.PASS;
	}

	/** Server: merge the new block into neighbouring walls. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof ExternalMonitorBlockEntity be) be.onPlaced();
	}

	/** Server: split the wall around the removed block (the block entity is still present here). */
	@SuppressWarnings("deprecation")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ExternalMonitorBlockEntity be) {
			be.destroy();
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if (level.getBlockEntity(pos) instanceof ExternalMonitorBlockEntity be) be.onNeighborChanged();
	}
}
