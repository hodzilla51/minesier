package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A placeable computer. Empty-hand right-click opens the terminal GUI on the
 * client; commands typed there run server-side in this block's sandboxed VM.
 */
public class ComputerBlock extends BaseEntityBlock {
	public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);

	public ComputerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<ComputerBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ComputerBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		// Insert a disk held in hand into an empty disk slot.
		if (stack.is(ModContent.DISK) && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer
				&& computer.getDisk().isEmpty()) {
			if (!level.isClientSide()) {
				computer.setDisk(stack.copyWithCount(1));
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
		// Not a disk — fall through so the empty-hand action (open terminal) still runs.
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
			ServerPlayNetworking.send(serverPlayer, new TerminalScreenS2C(pos, computer.getTranscript(), true));
		}
		return InteractionResult.SUCCESS;
	}
}
