package com.hodzilla51.minesier.block;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.hodzilla51.minesier.ModContent;
import com.hodzilla51.minesier.js.JsComputer;
import com.hodzilla51.minesier.js.NetworkApi;
import com.hodzilla51.minesier.net.CableNetwork;
import com.hodzilla51.minesier.net.NetworkFrame;
import com.hodzilla51.minesier.net.NetworkListener;
import com.hodzilla51.minesier.net.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
	private final EnumMap<Direction, NicState> nics = new EnumMap<>(Direction.class);
	private ItemStack disk = ItemStack.EMPTY;
	private String networkAddress = formatAddress(UUID.randomUUID());

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.COMPUTER_BLOCK_ENTITY, pos, state);
		for (Direction direction : Direction.values()) {
			nics.put(direction, new NicState());
		}
		computer.setNetwork(new ComputerNetworkApi());
	}

	public String getNetworkAddress() {
		return networkAddress;
	}

	/** Called by the physical cable medium for the NIC attached to {@code face}. */
	public void offerFrame(Direction face, NetworkFrame frame) {
		NicState nic = nics.get(face);
		if (nic == null || (!nic.promiscuous && !addressFor(face).equals(frame.destination()))) {
			return;
		}
		if (nic.listener != null) {
			NetworkListener listener = nic.listener;
			NetworkManager.schedule(queuedFrame -> {
				if (nic.listener == listener) {
					listener.onFrame(queuedFrame);
				}
			}, frame);
			return;
		}
		if (nic.inbox.size() >= MAX_INBOX_FRAMES) {
			return;
		}
		nic.inbox.addLast(frame);
		setChanged();
	}

	private NetworkFrame receiveFrame(Direction face) {
		NicState nic = nics.get(face);
		return nic == null ? null : nic.inbox.pollFirst();
	}

	private Direction legacyFace() {
		return getBlockState().getValue(ComputerBlock.FACING).getOpposite();
	}

	private String addressFor(Direction face) {
		if (face == legacyFace()) {
			return networkAddress;
		}
		return formatAddress(UUID.nameUUIDFromBytes(
				(networkAddress + "/" + face.getSerializedName()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	/** Resolves a player-facing NIC name against this computer's screen direction. */
	private Direction parseFace(String name) {
		Direction front = getBlockState().getValue(ComputerBlock.FACING);
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "front", "forward" -> front;
			case "back" -> front.getOpposite();
			case "left" -> front.getCounterClockWise();
			case "right" -> front.getClockWise();
			case "up" -> Direction.UP;
			case "down" -> Direction.DOWN;
			default -> null;
		};
	}

	/** Runs one program in this computer's VM, appending the echoed input + output to the transcript. */
	public void runCommand(String command) {
		computer.clearReceiveHandlers();
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
			return addressFor(legacyFace());
		}

		@Override
		public boolean send(String destination, String data) {
			return send(legacyFace(), destination, data);
		}

		@Override
		public NetworkFrame receive() {
			return receiveFrame(legacyFace());
		}

		@Override
		public String address(String interfaceName) {
			Direction face = parseFace(interfaceName);
			return face == null ? null : addressFor(face);
		}

		@Override
		public boolean send(String interfaceName, String destination, String data) {
			Direction face = parseFace(interfaceName);
			return face != null && send(face, destination, data);
		}

		private boolean send(Direction face, String destination, String data) {
			if (!(level instanceof ServerLevel serverLevel)
					|| destination.isBlank() || data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
				return false;
			}
			return CableNetwork.send(serverLevel, worldPosition, face, new NetworkFrame(addressFor(face), destination, data));
		}

		@Override
		public NetworkFrame receive(String interfaceName) {
			Direction face = parseFace(interfaceName);
			return face == null ? null : receiveFrame(face);
		}

		@Override
		public boolean forward(String interfaceName, NetworkFrame frame) {
			Direction face = parseFace(interfaceName);
			if (!(level instanceof ServerLevel serverLevel) || face == null || frame.destination().isBlank()
					|| frame.data().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
				return false;
			}
			return CableNetwork.send(serverLevel, worldPosition, face, frame);
		}

		@Override
		public boolean setPromiscuous(String interfaceName, boolean enabled) {
			Direction face = parseFace(interfaceName);
			NicState nic = face == null ? null : nics.get(face);
			if (nic == null) {
				return false;
			}
			nic.promiscuous = enabled;
			return true;
		}

		@Override
		public boolean setReceiveListener(String interfaceName, NetworkListener listener) {
			Direction face = parseFace(interfaceName);
			NicState nic = face == null ? null : nics.get(face);
			if (nic == null) {
				return false;
			}
			nic.listener = listener;
			return true;
		}

		@Override
		public boolean clearReceiveListener(String interfaceName) {
			Direction face = parseFace(interfaceName);
			NicState nic = face == null ? null : nics.get(face);
			if (nic == null) {
				return false;
			}
			nic.listener = null;
			return true;
		}

		@Override
		public void clearReceiveListeners() {
			for (NicState nic : nics.values()) {
				nic.listener = null;
			}
		}

		@Override
		public void reportOutput(List<String> lines) {
			if (lines.isEmpty()) {
				return;
			}
			transcript.addAll(lines);
			trim();
			setChanged();
		}
	}

	private static final class NicState {
		final Deque<NetworkFrame> inbox = new ArrayDeque<>();
		boolean promiscuous;
		NetworkListener listener;
	}

	@Override
	public void setRemoved() {
		computer.clearReceiveHandlers();
		super.setRemoved();
	}
}
