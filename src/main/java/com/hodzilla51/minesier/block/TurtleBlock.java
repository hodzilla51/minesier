package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A placeable programmable turtle. Right-click opens the same terminal as the
 * computer; commands run server-side and can move the turtle through the world.
 */
public class TurtleBlock extends BaseEntityBlock {
	public static final MapCodec<TurtleBlock> CODEC = simpleCodec(TurtleBlock::new);

	public TurtleBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<TurtleBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// The block entity renderer draws the turtle (so it can slide between blocks).
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TurtleBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		// Face the way the placer was looking, so "forward" goes into the world, not always north.
		if (!level.isClientSide() && placer != null && level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
			turtle.setFacing(placer.getDirection());
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Spill the turtle's inventory when a player breaks it (block hops never call this).
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
			Containers.dropContents(level, pos, turtle.getInventory());
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		// Insert a disk held in hand into an empty disk slot.
		if (stack.is(ModContent.DISK) && level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle
				&& turtle.getDisk().isEmpty()) {
			if (!level.isClientSide()) {
				turtle.setDisk(stack.copyWithCount(1));
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
				&& level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
			ServerPlayNetworking.send(serverPlayer, new TerminalScreenS2C(pos, turtle.getTranscript(), true));
		}
		return InteractionResult.SUCCESS;
	}
}
