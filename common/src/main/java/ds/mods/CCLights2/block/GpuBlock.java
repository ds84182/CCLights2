package ds.mods.CCLights2.block;

import org.jetbrains.annotations.Nullable;

import ds.mods.CCLights2.Registration;
import ds.mods.CCLights2.block.entity.GpuBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The GPU peripheral block (cube_bottom_top model, no facing). */
public class GpuBlock extends BaseEntityBlock {
	public GpuBlock(Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GpuBlockEntity(pos, state);
	}

	/** Both sides tick: the server flushes draw lists, the client replica finds its monitors and asks for a sync. */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide
				? createTickerHelper(type, Registration.GPU_BE.get(), GpuBlockEntity::clientTick)
				: createTickerHelper(type, Registration.GPU_BE.get(), GpuBlockEntity::serverTick);
	}

	/** Drops the installed RAM sticks when the block goes away. */
	@SuppressWarnings("deprecation")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof GpuBlockEntity be) {
			for (ItemStack stack : be.getRamDrops()) Block.popResource(level, pos, stack);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return level.getBlockEntity(pos) instanceof GpuBlockEntity be ? be.onUse(player, hand, hit) : InteractionResult.PASS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if (level.getBlockEntity(pos) instanceof GpuBlockEntity be) be.onNeighborChanged();
	}
}
