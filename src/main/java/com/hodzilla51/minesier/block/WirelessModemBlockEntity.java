package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.net.WirelessNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wireless antenna attached next to a computer. It carries no state of its own; it
 * just registers its position in {@link WirelessNetwork} while loaded so wireless
 * transmissions can find it without scanning the world. {@code clearRemoved} fires on
 * place/chunk-load and {@code setRemoved} on break/chunk-unload.
 */
public class WirelessModemBlockEntity extends BlockEntity {
	public WirelessModemBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WIRELESS_MODEM_BLOCK_ENTITY, pos, state);
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (level instanceof ServerLevel) {
			WirelessNetwork.register(level, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		if (level instanceof ServerLevel) {
			WirelessNetwork.unregister(level, worldPosition);
		}
		super.setRemoved();
	}
}
