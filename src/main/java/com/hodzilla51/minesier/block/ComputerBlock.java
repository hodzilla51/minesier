package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.MineSIerNet;
import com.hodzilla51.minesier.net.TerminalScreenS2C;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A placeable computer. Empty-hand right-click opens the terminal GUI on the client; commands typed
 * there run server-side in this block's sandboxed VM.
 */
public class ComputerBlock extends BaseEntityBlock {
  public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);

  /** Which way the front (screen) faces. */
  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

  public ComputerBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<ComputerBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(
      StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    // Front faces the player who placed it (furnace-style).
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ComputerBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    if (level.isClientSide()) {
      return null; // resident timers run server-side only
    }
    return createTickerHelper(
        type, ModContent.COMPUTER_BLOCK_ENTITY, (lvl, pos, st, be) -> be.serverTick());
  }

  @Override
  protected InteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    // Insert a disk held in hand into an empty disk slot.
    if (stack.is(ModContent.DISK)
        && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer
        && computer.getDisk().isEmpty()) {
      if (!level.isClientSide()) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || !computer.ensureAccess(serverPlayer)) {
          return InteractionResult.SUCCESS;
        }
        computer.setDisk(stack.copyWithCount(1));
        stack.shrink(1);
        // Slotting a disk boots its `startup.js` program (if any), so a daemon can auto-run.
        computer.bootStartup();
        MineSIerNet.sendProgramList(serverPlayer, computer);
      }
      return InteractionResult.SUCCESS;
    }
    // Not a disk — fall through so the empty-hand action (open terminal) still runs.
    return InteractionResult.TRY_WITH_EMPTY_HAND;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide()
        && player instanceof ServerPlayer serverPlayer
        && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
      if (player.isShiftKeyDown()) {
        computer.sendAccessPrompt(serverPlayer);
        return InteractionResult.SUCCESS;
      }
      if (!computer.ensureAccess(serverPlayer)) {
        return InteractionResult.SUCCESS;
      }
      ServerPlayNetworking.send(
          serverPlayer, new TerminalScreenS2C(pos, computer.getTranscript(), true));
      MineSIerNet.sendProgramList(serverPlayer, computer);
    }
    return InteractionResult.SUCCESS;
  }

  @Override
  protected boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    // {@code direction} points from the querying neighbor toward us; the face emitting toward
    // that neighbor is its opposite.
    if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
      return computer.getRedstoneOutput(direction.getOpposite());
    }
    return 0;
  }

  @Override
  protected int getDirectSignal(
      BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    // Emit the same strong (direct) power so the computer can drive dust through a solid block.
    return getSignal(state, level, pos, direction);
  }
}
