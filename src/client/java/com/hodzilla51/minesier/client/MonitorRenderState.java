package com.hodzilla51.minesier.client;

import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Per-frame render data for a monitor: which way the screen faces and the text to draw. */
public class MonitorRenderState extends BlockEntityRenderState {
  public Direction facing = Direction.NORTH;
  public List<String> lines = List.of();
}
