package com.hodzilla51.minesier.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wireless antenna. Place it next to a computer; the computer's face that touches it becomes a
 * wireless NIC, broadcasting to other modems within range.
 */
public class WirelessModemBlock extends BaseEntityBlock {
  public static final MapCodec<WirelessModemBlock> CODEC = simpleCodec(WirelessModemBlock::new);

  public WirelessModemBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<WirelessModemBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new WirelessModemBlockEntity(pos, state);
  }
}
