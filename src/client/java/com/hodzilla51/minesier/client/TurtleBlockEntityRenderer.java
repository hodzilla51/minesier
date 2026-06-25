package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.block.TurtleBlock;
import com.hodzilla51.minesier.block.TurtleBlockEntity;
import com.hodzilla51.minesier.net.TurtleVisualAction;
import com.hodzilla51.minesier.turtle.TurtleBrain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Draws the turtle (the block itself is INVISIBLE) so it can slide smoothly between cells during a
 * move. Renders the turtle's block model through the moving-block path, translated by the current
 * slide offset interpolated from {@link TurtleAnimations}.
 */
public class TurtleBlockEntityRenderer
    implements BlockEntityRenderer<TurtleBlockEntity, TurtleRenderState> {

  private static final float SCREEN_SCALE = 0.035f;
  private static final int FULL_BRIGHT = 0xF000F0;
  private static final int FOOT_COLOR = 0xFF66D9EF;
  private static final int ARM_COLOR = 0xFFFFD166;
  private static final int TOP_COLOR = 0xFFB48CFF;
  private static final VoxelShape FOOT_SHAPE =
      Shapes.or(
          Shapes.box(0.08, -0.08, 0.08, 0.24, 0.10, 0.92),
          Shapes.box(0.76, -0.08, 0.08, 0.92, 0.10, 0.92));
  private static final VoxelShape ARM_SHAPE = Shapes.box(0.92, 0.28, 0.24, 1.18, 0.48, 0.76);
  private static final VoxelShape TOP_SHAPE = Shapes.box(0.30, 0.96, 0.30, 0.70, 1.18, 0.70);

  private final Font font;
  private final ItemModelResolver itemModelResolver;

  public TurtleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.font = context.font();
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public TurtleRenderState createRenderState() {
    return new TurtleRenderState();
  }

  @Override
  public void extractRenderState(
      TurtleBlockEntity be,
      TurtleRenderState state,
      float partialTick,
      Vec3 cameraPos,
      ModelFeatureRenderer.CrumblingOverlay crumbling) {
    BlockEntityRenderState.extractBase(be, state, crumbling);

    Level level = be.getLevel();
    BlockPos pos = be.getBlockPos();
    state.moving = createMovingBlock(pos, be.getBlockState(), level);
    state.facing = be.getBlockState().getValue(TurtleBlock.FACING);
    long gameTime = level.getGameTime();

    state.offsetX = 0f;
    state.offsetY = 0f;
    state.offsetZ = 0f;
    state.bobY = 0.008f + (float) Math.sin((gameTime + partialTick) * 0.18f) * 0.008f;
    state.tiltXDeg = 0f;
    state.tiltZDeg = 0f;
    state.screenText = ((gameTime / 6) & 1) == 0 ? ">_" : "> ";
    state.screenColor = 0xFF9CFF9C;
    state.pickupItem.clear();
    state.showPickupItem = false;
    state.footItem.clear();
    state.armItem.clear();
    state.topItem.clear();
    state.hasFootEquipment = !be.getEquipment().get(TurtleBlockEntity.EQUIPMENT_FOOT).isEmpty();
    state.hasArmEquipment = !be.getEquipment().get(TurtleBlockEntity.EQUIPMENT_ARM).isEmpty();
    state.hasTopEquipment = !be.getEquipment().get(TurtleBlockEntity.EQUIPMENT_TOP).isEmpty();
    configureEquipmentItems(state, be, level);

    TurtleAnimations.Slide slide = TurtleAnimations.get(pos);
    if (slide != null) {
      float progress = (gameTime + partialTick - slide.startTick()) / slide.durationTicks();
      if (progress >= 0f && progress < 1f) {
        applyHop(state, slide.fromDir(), progress);
        state.screenText = "<>";
      }
    }

    state.turnDeg = 0f;
    TurtleAnimations.Turn turn = TurtleAnimations.getTurn(pos);
    if (turn != null) {
      float progress = (gameTime + partialTick - turn.startTick()) / TurtleBrain.TURN_TICKS;
      if (progress >= 0f && progress < 1f) {
        // Start at the old heading, spin slightly past the final heading, then settle back.
        state.turnDeg = turn.deltaDeg() * (1f - easeOutBack(progress));
        state.screenText = "<>";
      }
    }

    TurtleAnimations.Effect effect = TurtleAnimations.getEffect(pos, gameTime);
    if (effect != null) {
      float progress =
          (gameTime + partialTick - effect.startTick()) / TurtleAnimations.EFFECT_TICKS;
      if (progress >= 0f && progress < 1f) {
        applyEffect(state, effect, progress);
        if (effect.action() == TurtleVisualAction.PICKUP) {
          configurePickupItem(state, level, effect.detail());
        }
      }
    }
  }

  @Override
  public void submit(
      TurtleRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState cameraRenderState) {
    poseStack.pushPose();
    poseStack.translate(state.offsetX, state.offsetY + state.bobY, state.offsetZ);
    if (state.turnDeg != 0f) {
      // Rotate around the block's vertical center axis to ease the turn.
      poseStack.rotateAround(Axis.YP.rotationDegrees(state.turnDeg), 0.5f, 0.5f, 0.5f);
    }
    if (state.tiltXDeg != 0f) {
      poseStack.rotateAround(Axis.XP.rotationDegrees(state.tiltXDeg), 0.5f, 0.5f, 0.5f);
    }
    if (state.tiltZDeg != 0f) {
      poseStack.rotateAround(Axis.ZP.rotationDegrees(state.tiltZDeg), 0.5f, 0.5f, 0.5f);
    }
    // The final argument is an optional outline color, not a light coordinate.
    collector.submitMovingBlock(poseStack, state.moving, 0);
    submitEquipmentShapes(state, poseStack, collector);
    submitEquipment(state, poseStack, collector);
    submitScreen(state, poseStack, collector);
    poseStack.popPose();
  }

  private void applyHop(TurtleRenderState state, Direction fromDir, float progress) {
    float travel;
    if (progress < 0.18f) {
      // Wind up in the opposite direction before leaving the cell.
      travel = 0f;
    } else {
      travel = easeOutBack((progress - 0.18f) / 0.82f);
    }
    float pullBack = progress < 0.18f ? (progress / 0.18f) * 0.075f : 0f;
    state.offsetX = fromDir.getStepX() * (1f - travel + pullBack);
    state.offsetY = fromDir.getStepY() * (1f - travel + pullBack);
    state.offsetZ = fromDir.getStepZ() * (1f - travel + pullBack);
    float airborne = Math.max(0f, Math.min(1f, (progress - 0.12f) / 0.72f));
    state.bobY += (float) Math.sin(airborne * Math.PI) * 0.11f;

    Direction travelDir = fromDir.getOpposite();
    float tilt = (float) Math.sin(airborne * Math.PI) * 10f;
    state.tiltXDeg = -travelDir.getStepZ() * tilt;
    state.tiltZDeg = travelDir.getStepX() * tilt;
  }

  private void applyEffect(
      TurtleRenderState state, TurtleAnimations.Effect effect, float progress) {
    float pulse = (float) Math.sin(progress * Math.PI);
    state.screenText = compactScreenText(effect.detail());
    switch (effect.action()) {
      case DIG -> {
        state.bobY += pulse * 0.025f;
        state.tiltXDeg += state.facing.getStepZ() * pulse * 7f;
        state.tiltZDeg -= state.facing.getStepX() * pulse * 7f;
        state.screenColor = 0xFFFFE38A;
      }
      case PLACE -> {
        state.bobY += pulse * 0.018f;
        state.tiltXDeg -= state.facing.getStepZ() * pulse * 5f;
        state.tiltZDeg += state.facing.getStepX() * pulse * 5f;
        state.screenColor = 0xFF9CFF9C;
      }
      case PICKUP -> {
        state.bobY += pulse * 0.09f;
        state.screenColor = 0xFFFFFF9C;
      }
      case OUT_OF_FUEL -> {
        state.bobY += progress < 0.35f ? pulse * 0.055f : 0f;
        state.screenColor = 0xFFFFD34D;
      }
      case ERROR -> state.screenColor = 0xFFFF5555;
      case NET_SEND -> {
        state.screenText = networkScreenText(progress, true);
        state.screenColor = 0xFF8AD8FF;
      }
      case NET_RECEIVE -> {
        state.screenText = networkScreenText(progress, false);
        state.screenColor = 0xFF8AD8FF;
      }
      case NET_FORWARD -> {
        state.screenText = progress < 0.33f ? ">." : progress < 0.66f ? ".>" : ">>";
        state.screenColor = 0xFF8AD8FF;
      }
    }
  }

  private void submitScreen(
      TurtleRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);
    poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
    poseStack.translate(0.0, 0.0, 0.501);
    poseStack.scale(SCREEN_SCALE, -SCREEN_SCALE, SCREEN_SCALE);
    FormattedCharSequence text = FormattedCharSequence.forward(state.screenText, Style.EMPTY);
    poseStack.translate(-font.width(text) / 2f, -font.lineHeight / 2f, 0f);
    collector.submitText(
        poseStack,
        0f,
        0f,
        text,
        false,
        Font.DisplayMode.POLYGON_OFFSET,
        FULL_BRIGHT,
        state.screenColor,
        0,
        0);
    if (state.showPickupItem) {
      poseStack.pushPose();
      poseStack.scale(0.45f, 0.45f, 0.45f);
      state.pickupItem.submit(poseStack, collector, FULL_BRIGHT, 0, 0);
      poseStack.popPose();
    }
    poseStack.popPose();
  }

  private void configureEquipmentItems(TurtleRenderState state, TurtleBlockEntity be, Level level) {
    configureEquipmentItem(
        state.footItem, be.getEquipment().get(TurtleBlockEntity.EQUIPMENT_FOOT), level);
    configureEquipmentItem(
        state.armItem, be.getEquipment().get(TurtleBlockEntity.EQUIPMENT_ARM), level);
    configureEquipmentItem(
        state.topItem, be.getEquipment().get(TurtleBlockEntity.EQUIPMENT_TOP), level);
  }

  private void configureEquipmentItem(
      net.minecraft.client.renderer.item.ItemStackRenderState renderState,
      ItemStack stack,
      Level level) {
    if (stack.isEmpty()) {
      return;
    }
    itemModelResolver.updateForTopItem(
        renderState, stack, ItemDisplayContext.FIXED, level, null, 0);
  }

  private void submitEquipment(
      TurtleRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);
    poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));

    if (!state.footItem.isEmpty()) {
      poseStack.pushPose();
      poseStack.translate(0.0, -0.45, -0.18);
      poseStack.scale(0.32f, 0.32f, 0.32f);
      state.footItem.submit(poseStack, collector, FULL_BRIGHT, 0, 0);
      poseStack.popPose();
    }

    if (!state.armItem.isEmpty()) {
      poseStack.pushPose();
      poseStack.translate(0.42, -0.03, 0.28);
      poseStack.mulPose(Axis.ZP.rotationDegrees(-35f));
      poseStack.scale(0.34f, 0.34f, 0.34f);
      state.armItem.submit(poseStack, collector, FULL_BRIGHT, 0, 0);
      poseStack.popPose();
    }

    if (!state.topItem.isEmpty()) {
      poseStack.pushPose();
      poseStack.translate(0.0, 0.56, 0.0);
      poseStack.mulPose(Axis.XP.rotationDegrees(90f));
      poseStack.scale(0.30f, 0.30f, 0.30f);
      state.topItem.submit(poseStack, collector, FULL_BRIGHT, 0, 0);
      poseStack.popPose();
    }

    poseStack.popPose();
  }

  private void submitEquipmentShapes(
      TurtleRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);
    poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
    poseStack.translate(-0.5, -0.5, -0.5);

    if (state.hasFootEquipment) {
      collector.submitShapeOutline(
          poseStack, FOOT_SHAPE, RenderTypes.lines(), FOOT_COLOR, 3.0f, false);
    }
    if (state.hasArmEquipment) {
      collector.submitShapeOutline(
          poseStack, ARM_SHAPE, RenderTypes.lines(), ARM_COLOR, 3.0f, false);
    }
    if (state.hasTopEquipment) {
      collector.submitShapeOutline(
          poseStack, TOP_SHAPE, RenderTypes.lines(), TOP_COLOR, 3.0f, false);
    }

    poseStack.popPose();
  }

  private void configurePickupItem(TurtleRenderState state, Level level, String itemId) {
    Identifier id = Identifier.tryParse(itemId);
    if (id == null) {
      return;
    }
    var item = BuiltInRegistries.ITEM.getValue(id);
    if (!BuiltInRegistries.ITEM.getKey(item).equals(id)) {
      return;
    }
    itemModelResolver.updateForTopItem(
        state.pickupItem, new ItemStack(item), ItemDisplayContext.GUI, level, null, 0);
    state.showPickupItem = !state.pickupItem.isEmpty();
    if (state.showPickupItem) {
      state.screenText = "";
    }
  }

  private static float easeOutBack(float progress) {
    float x = Math.max(0f, Math.min(1f, progress)) - 1f;
    return 1f + 2.70158f * x * x * x + 1.70158f * x * x;
  }

  private static String compactScreenText(String value) {
    return value.length() <= 3 ? value : value.substring(0, 3);
  }

  /** A tiny Windows-style transfer: folder ([]), paper (.), and network globe (@). */
  private static String networkScreenText(float progress, boolean sending) {
    if (progress < 0.33f) {
      return sending ? "[]" : "@";
    }
    if (progress < 0.66f) {
      return ".";
    }
    return sending ? "@" : "[]";
  }

  private static MovingBlockRenderState createMovingBlock(
      BlockPos pos, net.minecraft.world.level.block.state.BlockState blockState, Level level) {
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
