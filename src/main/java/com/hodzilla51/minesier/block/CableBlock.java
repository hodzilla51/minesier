package com.hodzilla51.minesier.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;

/** A wired shared-medium segment. Connections are calculated by {@code CableNetwork}. */
public class CableBlock extends Block {
	public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

	public CableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
}
