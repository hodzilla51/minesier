package com.hodzilla51.minesier.client;

import com.hodzilla51.minesier.block.MonitorBlock;
import com.hodzilla51.minesier.block.MonitorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Monitor's text buffer on its screen (front) face. The block model renders normally;
 * this adds the text on top of the {@code facing} face using {@code POLYGON_OFFSET} so it sits
 * flush without z-fighting.
 */
public class MonitorBlockEntityRenderer
    implements BlockEntityRenderer<MonitorBlockEntity, MonitorRenderState> {

  /** Font line height in pixels; the logical grid is ROWS lines tall. */
  private static final float LINE_HEIGHT = 9f;

  /** Approximate pixel width of one column, used to size the grid to the block face. */
  private static final float COLUMN_WIDTH = 6f;

  private static final float GRID_WIDTH = MonitorBlockEntity.COLUMNS * COLUMN_WIDTH;
  private static final float GRID_HEIGHT = MonitorBlockEntity.ROWS * LINE_HEIGHT;

  /** Fraction of the block face the text grid spans; the rest is a margin. */
  private static final float FACE_FILL = 0.85f;

  private static final float SCALE = FACE_FILL / GRID_WIDTH;
  private static final int TEXT_COLOR = 0xFFFFFFFF; // opaque white (ARGB)

  private final Font font;

  public MonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.font = context.font();
  }

  @Override
  public MonitorRenderState createRenderState() {
    return new MonitorRenderState();
  }

  @Override
  public void extractRenderState(
      MonitorBlockEntity be,
      MonitorRenderState state,
      float partialTick,
      Vec3 cameraPos,
      ModelFeatureRenderer.CrumblingOverlay crumbling) {
    BlockEntityRenderState.extractBase(be, state, crumbling);
    state.facing = be.getBlockState().getValue(MonitorBlock.FACING);
    state.lines = new ArrayList<>(be.lines());
  }

  @Override
  public void submit(
      MonitorRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState cameraRenderState) {
    if (state.lines.isEmpty()) {
      return;
    }
    Direction facing = state.facing;

    poseStack.pushPose();
    poseStack.translate(0.5, 0.5, 0.5);
    // Orient the text plane so the font's +Z normal points out along `facing` (toward the viewer).
    poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
    // Move out to the screen face surface.
    poseStack.translate(0.0, 0.0, 0.5);
    // Font draws with +Y downward, so flip Y; shrink the pixel grid onto the block face.
    poseStack.scale(SCALE, -SCALE, SCALE);
    // Origin to the top-left of the centered grid (font pixel space).
    poseStack.translate(-GRID_WIDTH / 2f, -GRID_HEIGHT / 2f, 0f);

    for (int row = 0; row < state.lines.size() && row < MonitorBlockEntity.ROWS; row++) {
      String line = state.lines.get(row);
      if (line.length() > MonitorBlockEntity.COLUMNS) {
        line = line.substring(0, MonitorBlockEntity.COLUMNS);
      }
      FormattedCharSequence text = FormattedCharSequence.forward(line, Style.EMPTY);
      collector.submitText(
          poseStack,
          0f,
          row * LINE_HEIGHT,
          text,
          false,
          Font.DisplayMode.POLYGON_OFFSET,
          state.lightCoords,
          TEXT_COLOR,
          0,
          0);
    }
    poseStack.popPose();
  }
}
