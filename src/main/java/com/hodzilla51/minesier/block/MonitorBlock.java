package com.hodzilla51.minesier.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A placeable display surface. The block itself is inert; an adjacent computer writes text to it
 * through the sandboxed {@code monitor} global, and {@code MonitorBlockEntityRenderer} draws that
 * text on the screen (front) face in the world.
 */
public class MonitorBlock extends BaseEntityBlock {
  public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);

  /** Which way the screen (front) face points. */
  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

  public MonitorBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<MonitorBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    // Screen faces the player who placed it (furnace-style), matching the Computer block.
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new MonitorBlockEntity(pos, state);
  }
}
