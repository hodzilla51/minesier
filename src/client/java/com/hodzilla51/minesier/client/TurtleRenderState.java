package com.hodzilla51.minesier.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Per-frame render data for a turtle: the block model to draw + its slide offset. */
public class TurtleRenderState extends BlockEntityRenderState {
  public MovingBlockRenderState moving;
  public float offsetX;
  public float offsetY;
  public float offsetZ;

  /** Extra yaw (degrees) eased away from the final facing while turning; 0 when settled. */
  public float turnDeg;
}
