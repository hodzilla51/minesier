package com.hodzilla51.minesier.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

/** Per-frame render data for a turtle: the block model to draw + its slide offset. */
public class TurtleRenderState extends BlockEntityRenderState {
  public MovingBlockRenderState moving;
  public float offsetX;
  public float offsetY;
  public float offsetZ;

  /** Extra yaw (degrees) eased away from the final facing while turning; 0 when settled. */
  public float turnDeg;

  /** Small pitch/roll used to make a hop feel like a physical little machine. */
  public float tiltXDeg;

  public float tiltZDeg;

  /** A short vertical lift for hops and action recoil. */
  public float bobY;

  /** The turtle's front, used to orient its screen overlay. */
  public Direction facing = Direction.NORTH;

  /** The short status text currently shown on the turtle's front display. */
  public String screenText = ">_";

  /** ARGB display color. */
  public int screenColor = 0xFF9CFF9C;

  /** Model data for the short item-pickup icon shown on the front display. */
  public final ItemStackRenderState pickupItem = new ItemStackRenderState();

  public boolean showPickupItem;

  /** Simple equipped-part placeholders rendered on the turtle body. */
  public final ItemStackRenderState footItem = new ItemStackRenderState();

  public boolean footItemUsesTurtleSpace;

  public final ItemStackRenderState armItem = new ItemStackRenderState();

  public final ItemStackRenderState topItem = new ItemStackRenderState();
}
