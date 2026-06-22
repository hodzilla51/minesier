package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.turtle.TurtleBrain;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the turtle (the block itself is INVISIBLE) so it can slide smoothly between
 * cells during a move. Renders the turtle's block model via {@code submitMovingBlock}
 * — the same path pistons use for pushed blocks — translated by the current slide
 * offset interpolated from {@link TurtleAnimations}.
 */
public class TurtleBlockEntityRenderer implements BlockEntityRenderer<TurtleBlockEntity, TurtleRenderState> {

	public TurtleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public TurtleRenderState createRenderState() {
		return new TurtleRenderState();
	}

	@Override
	public void extractRenderState(TurtleBlockEntity be, TurtleRenderState state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(be, state, crumbling);

		Level level = be.getLevel();
		BlockPos pos = be.getBlockPos();
		state.moving = createMovingBlock(pos, be.getBlockState(), level);

		state.offsetX = 0f;
		state.offsetY = 0f;
		state.offsetZ = 0f;
		TurtleAnimations.Slide slide = TurtleAnimations.get(pos);
		if (slide != null) {
			float progress = (level.getGameTime() + partialTick - slide.startTick()) / TurtleBrain.PACE_TICKS;
			if (progress >= 0f && progress < 1f) {
				float back = 1f - progress; // 1 at A, 0 at B
				state.offsetX = slide.fromDir().getStepX() * back;
				state.offsetY = slide.fromDir().getStepY() * back;
				state.offsetZ = slide.fromDir().getStepZ() * back;
			}
		}
	}

	@Override
	public void submit(TurtleRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState cameraRenderState) {
		if (state.moving == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
		collector.submitMovingBlock(poseStack, state.moving, state.lightCoords);
		poseStack.popPose();
	}

	private static MovingBlockRenderState createMovingBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState blockState,
			Level level) {
		MovingBlockRenderState moving = new MovingBlockRenderState();
		moving.randomSeedPos = pos;
		moving.blockPos = pos;
		moving.blockState = blockState;
		moving.biome = level.getBiome(pos);
		if (level instanceof ClientLevel clientLevel) {
			moving.cardinalLighting = clientLevel.cardinalLighting();
			moving.lightEngine = clientLevel.getLightEngine();
		}
		return moving;
	}
}
