package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A wired shared-medium segment. Connections are calculated by {@code CableNetwork}. */
public class CableBlock extends Block {
  public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

  public static final BooleanProperty NORTH = BooleanProperty.create("north");
  public static final BooleanProperty SOUTH = BooleanProperty.create("south");
  public static final BooleanProperty EAST = BooleanProperty.create("east");
  public static final BooleanProperty WEST = BooleanProperty.create("west");
  public static final BooleanProperty UP = BooleanProperty.create("up");
  public static final BooleanProperty DOWN = BooleanProperty.create("down");

  private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
  private static final VoxelShape ARM_NORTH = Block.box(6, 6, 0, 10, 10, 6);
  private static final VoxelShape ARM_SOUTH = Block.box(6, 6, 10, 10, 10, 16);
  private static final VoxelShape ARM_WEST = Block.box(0, 6, 6, 6, 10, 10);
  private static final VoxelShape ARM_EAST = Block.box(10, 6, 6, 16, 10, 10);
  private static final VoxelShape ARM_DOWN = Block.box(6, 0, 6, 10, 6, 10);
  private static final VoxelShape ARM_UP = Block.box(6, 10, 6, 10, 16, 10);

  public CableBlock(Properties properties) {
    super(properties.noOcclusion());
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(NORTH, false)
            .setValue(SOUTH, false)
            .setValue(EAST, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return computeState(defaultBlockState(), context.getLevel(), context.getClickedPos());
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      LevelReader level,
      ScheduledTickAccess tickAccess,
      BlockPos pos,
      Direction direction,
      BlockPos neighborPos,
      BlockState neighborState,
      RandomSource random) {
    return state.setValue(prop(direction), connects(neighborState));
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    VoxelShape shape = CORE;
    if (state.getValue(NORTH)) shape = Shapes.or(shape, ARM_NORTH);
    if (state.getValue(SOUTH)) shape = Shapes.or(shape, ARM_SOUTH);
    if (state.getValue(EAST)) shape = Shapes.or(shape, ARM_EAST);
    if (state.getValue(WEST)) shape = Shapes.or(shape, ARM_WEST);
    if (state.getValue(UP)) shape = Shapes.or(shape, ARM_UP);
    if (state.getValue(DOWN)) shape = Shapes.or(shape, ARM_DOWN);
    return shape;
  }

  private BlockState computeState(BlockState state, BlockGetter level, BlockPos pos) {
    return state
        .setValue(NORTH, connects(level.getBlockState(pos.relative(Direction.NORTH))))
        .setValue(SOUTH, connects(level.getBlockState(pos.relative(Direction.SOUTH))))
        .setValue(EAST, connects(level.getBlockState(pos.relative(Direction.EAST))))
        .setValue(WEST, connects(level.getBlockState(pos.relative(Direction.WEST))))
        .setValue(UP, connects(level.getBlockState(pos.relative(Direction.UP))))
        .setValue(DOWN, connects(level.getBlockState(pos.relative(Direction.DOWN))));
  }

  private static boolean connects(BlockState state) {
    return state.is(ModContent.CABLE_BLOCK)
        || state.is(ModContent.COMPUTER_BLOCK)
        || state.is(ModContent.TURTLE_BLOCK)
        || state.is(ModContent.SWITCH_BLOCK)
        || state.is(ModContent.WIRELESS_MODEM_BLOCK);
  }

  private static BooleanProperty prop(Direction d) {
    return switch (d) {
      case NORTH -> NORTH;
      case SOUTH -> SOUTH;
      case EAST -> EAST;
      case WEST -> WEST;
      case UP -> UP;
      case DOWN -> DOWN;
    };
  }
}
