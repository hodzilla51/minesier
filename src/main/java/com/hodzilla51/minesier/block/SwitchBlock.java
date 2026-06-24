package com.hodzilla51.minesier.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A beginner-friendly managed layer-2 learning switch. */
public class SwitchBlock extends BaseEntityBlock {
  public static final MapCodec<SwitchBlock> CODEC = simpleCodec(SwitchBlock::new);

  public SwitchBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new SwitchBlockEntity(pos, state);
  }
}
