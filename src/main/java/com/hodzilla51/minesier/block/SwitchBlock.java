package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.net.SwitchStatusS2C;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide()
        && player instanceof ServerPlayer serverPlayer
        && level.getBlockEntity(pos) instanceof SwitchBlockEntity networkSwitch) {
      if (player.isShiftKeyDown()) {
        networkSwitch.sendAccessPrompt(serverPlayer);
        return InteractionResult.SUCCESS;
      }
      if (!networkSwitch.ensureAccess(serverPlayer)) {
        return InteractionResult.SUCCESS;
      }
      ServerPlayNetworking.send(serverPlayer, new SwitchStatusS2C(pos, networkSwitch.statusText()));
    }
    return InteractionResult.SUCCESS;
  }
}
