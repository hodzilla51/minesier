package com.hodzilla51.minesier.net;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * MineSIer's IPv4-inspired layer-3 packet.
 *
 * <p>Its header follows IPv4 concepts (source, destination, TTL, and protocol)
 * while deliberately omitting checksums, fragmentation, and options from the
 * lossless MVP. The wire format is an internal, string-safe envelope carried in
 * a layer-2 {@link NetworkFrame} payload; it is not an RFC 791 byte encoding.
 */
public record IpPacket(String source, String destination, int ttl, int protocol, String payload) {
	private static final String PREFIX = "MSIP4|";

	public IpPacket {
		validateAddress(source);
		validateAddress(destination);
		if (ttl < 1 || ttl > 255) {
			throw new IllegalArgumentException("TTL must be between 1 and 255");
		}
		if (protocol < 0 || protocol > 255) {
			throw new IllegalArgumentException("protocol must be between 0 and 255");
		}
	}

	public String encode() {
		return PREFIX + source + "|" + destination + "|" + ttl + "|" + protocol + "|"
			+ Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/** Returns null for a non-IP payload or a malformed packet. */
	public static IpPacket decode(String wire) {
		if (!wire.startsWith(PREFIX)) {
			return null;
		}
		String[] fields = wire.split("\\|", 6);
		if (fields.length != 6 || !"MSIP4".equals(fields[0])) {
			return null;
		}
		try {
			return new IpPacket(fields[1], fields[2], Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
				new String(Base64.getDecoder().decode(fields[5]), StandardCharsets.UTF_8));
		} catch (IllegalArgumentException malformed) {
			return null;
		}
	}

	/** Decrements TTL for one routed hop, or returns null when the packet expires. */
	public IpPacket routed() {
		return ttl == 1 ? null : new IpPacket(source, destination, ttl - 1, protocol, payload);
	}

	private static void validateAddress(String address) {
		String[] octets = address.split("\\.", -1);
		if (octets.length != 4) {
			throw new IllegalArgumentException("expected an IPv4 address");
		}
		for (String octet : octets) {
			try {
				int value = Integer.parseInt(octet);
				if (value < 0 || value > 255 || !Integer.toString(value).equals(octet)) {
					throw new IllegalArgumentException("expected an IPv4 address");
				}
			} catch (NumberFormatException invalidOctet) {
				throw new IllegalArgumentException("expected an IPv4 address", invalidOctet);
			}
		}
	}
}
