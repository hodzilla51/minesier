package com.hodzilla51.minesier.block;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.js.NetworkApi;
import com.hodzilla51.minesier.net.CableNetwork;
import com.hodzilla51.minesier.net.NetworkFrame;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Backing state + VM for a placed Computer block.
 *
 * <p>Holds a scrollback transcript (input echoes + results) which is persisted
 * to NBT. The {@link JsComputer} (live scope) is transient, rebuilt on load —
 * variables defined in a previous session are intentionally not restored yet.
 */
public class ComputerBlockEntity extends BlockEntity implements ProgramStore {
	private static final String KEY_TRANSCRIPT = "Transcript";
	private static final String KEY_DISK = "Disk";
	private static final String KEY_ADDRESS = "NetworkAddress";
	private static final int MAX_LINES = 200;
	private static final int MAX_INBOX_FRAMES = 64;
	private static final int MAX_FRAME_BYTES = 4 * 1024;
	private static final String WELCOME = "MineSIer JS terminal — type an expression.";

	/** This computer's own sandboxed JS VM (1 block = 1 VM). */
	private final JsComputer computer = new JsComputer();

	private final List<String> transcript = new ArrayList<>(List.of(WELCOME));
	private final Deque<NetworkFrame> inbox = new ArrayDeque<>();
	private ItemStack disk = ItemStack.EMPTY;
	private String networkAddress = formatAddress(UUID.randomUUID());
	/** Kept in the NIC model now; slice 2 will expose a JS control for it. */
	private boolean promiscuous;

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.COMPUTER_BLOCK_ENTITY, pos, state);
		computer.setNetwork(new ComputerNetworkApi());
	}

	public String getNetworkAddress() {
		return networkAddress;
	}

	/** Called by the physical cable medium. Default NICs only accept frames addressed to themselves. */
	public void offerFrame(NetworkFrame frame) {
		if ((!promiscuous && !networkAddress.equals(frame.destination())) || inbox.size() >= MAX_INBOX_FRAMES) {
			return;
		}
		inbox.addLast(frame);
		setChanged();
	}

	private NetworkFrame receiveFrame() {
		return inbox.pollFirst();
	}

	/** Runs one program in this computer's VM, appending the echoed input + output to the transcript. */
	public void runCommand(String command) {
		String[] inputLines = command.split("\n", -1);
		transcript.add("> " + inputLines[0]);
		for (int i = 1; i < inputLines.length; i++) {
			transcript.add("  " + inputLines[i]); // continuation lines, indented
		}
		transcript.addAll(computer.run(command));
		trim();
		setChanged();
	}

	/** The full scrollback joined with newlines (for sending to the client). */
	@Override
	public String getTranscript() {
		return String.join("\n", transcript);
	}

	@Override
	public ItemStack getDisk() {
		return disk;
	}

	public void setDisk(ItemStack disk) {
		this.disk = disk;
		setChanged();
	}

	@Override
	public void markChanged() {
		setChanged();
	}

	private void trim() {
		while (transcript.size() > MAX_LINES) {
			transcript.remove(0);
		}
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		String saved = in.getStringOr(KEY_TRANSCRIPT, WELCOME);
		transcript.clear();
		for (String line : saved.split("\n", -1)) {
			transcript.add(line);
		}
		this.disk = in.read(KEY_DISK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
		this.networkAddress = in.getStringOr(KEY_ADDRESS, networkAddress);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString(KEY_TRANSCRIPT, getTranscript());
		if (!disk.isEmpty()) {
			out.store(KEY_DISK, ItemStack.CODEC, disk);
		}
		out.putString(KEY_ADDRESS, networkAddress);
	}

	private static String formatAddress(UUID uuid) {
		long value = uuid.getLeastSignificantBits();
		StringBuilder result = new StringBuilder(17);
		for (int i = 5; i >= 0; i--) {
			if (result.length() > 0) {
				result.append(':');
			}
			int octet = (int) (value >>> (i * 8)) & 0xff;
			if (i == 5) {
				octet = (octet & 0xfe) | 0x02; // locally administered, unicast
			}
			result.append(String.format("%02x", octet));
		}
		return result.toString();
	}

	private final class ComputerNetworkApi implements NetworkApi {
		@Override
		public String address() {
			return networkAddress;
		}

		@Override
		public boolean send(String destination, String data) {
			if (!(level instanceof ServerLevel serverLevel)
					|| destination.isBlank() || data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
				return false;
			}
			return CableNetwork.send(serverLevel, worldPosition, new NetworkFrame(networkAddress, destination, data));
		}

		@Override
		public NetworkFrame receive() {
			return receiveFrame();
		}
	}
}
