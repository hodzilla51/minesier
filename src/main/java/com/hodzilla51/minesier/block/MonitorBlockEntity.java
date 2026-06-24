package com.hodzilla51.minesier.block;

import com.hodzilla51.minesier.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Backing text buffer for a placed Monitor block.
 *
 * <p>Holds a small fixed grid of text lines that a neighbouring computer drives through the {@code
 * monitor} global. The buffer is persisted to NBT and synced to clients (via the standard block
 * entity update packet) so {@code MonitorBlockEntityRenderer} can draw it on the screen face.
 */
public class MonitorBlockEntity extends BlockEntity {
  /** Logical grid the {@code monitor} API reports and the renderer lays out. */
  public static final int ROWS = 12;

  public static final int COLUMNS = 26;

  private static final String KEY_LINES = "Lines";

  private final List<String> lines = new ArrayList<>();

  public MonitorBlockEntity(BlockPos pos, BlockState state) {
    super(ModContent.MONITOR_BLOCK_ENTITY, pos, state);
  }

  /** A snapshot of the current lines (top to bottom) for the renderer. */
  public List<String> lines() {
    return lines;
  }

  /** Appends {@code text} (splitting on newlines), scrolling the oldest lines off the top. */
  public void write(String text) {
    for (String line : text.split("\n", -1)) {
      lines.add(line);
    }
    while (lines.size() > ROWS) {
      lines.remove(0);
    }
    markUpdated();
  }

  /** Sets row {@code row} (1-based, 1..ROWS), padding earlier rows with blanks as needed. */
  public boolean setLine(int row, String text) {
    if (row < 1 || row > ROWS) {
      return false;
    }
    while (lines.size() < row) {
      lines.add("");
    }
    lines.set(row - 1, text);
    markUpdated();
    return true;
  }

  /** Replaces the whole buffer with {@code text} (newlines split into rows). */
  public void setText(String text) {
    lines.clear();
    write(text);
  }

  /** Clears the screen. */
  public void clear() {
    lines.clear();
    markUpdated();
  }

  /** Persists, then asks the server to resync this block entity to nearby clients. */
  private void markUpdated() {
    setChanged();
    if (level != null && !level.isClientSide()) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
  }

  @Override
  protected void loadAdditional(ValueInput in) {
    super.loadAdditional(in);
    lines.clear();
    String saved = in.getStringOr(KEY_LINES, "");
    if (!saved.isEmpty()) {
      for (String line : saved.split("\n", -1)) {
        lines.add(line);
      }
    }
  }

  @Override
  protected void saveAdditional(ValueOutput out) {
    super.saveAdditional(out);
    out.putString(KEY_LINES, String.join("\n", lines));
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return saveCustomOnly(registries);
  }

  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
