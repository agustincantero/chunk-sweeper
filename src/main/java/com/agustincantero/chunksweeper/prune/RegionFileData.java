package com.agustincantero.chunksweeper.prune;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory view of an Anvil region file (.mca) that can be rewritten with only a subset of its entries.
 * Entries are copied byte for byte, so chunks are never decompressed and recompressed when writing.
 */
public final class RegionFileData {
	public static final Pattern FILE_NAME = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
	public static final int ENTRY_COUNT = 1024;
	public static final int SECTOR_BYTES = 4096;
	private static final int HEADER_BYTES = 2 * SECTOR_BYTES;
	private static final int ENTRY_HEADER_BYTES = 5;
	private static final int EXTERNAL_FLAG = 0x80;

	private final byte[] bytes;
	private final ByteBuffer buffer;

	private RegionFileData(byte[] bytes) {
		this.bytes = bytes;
		this.buffer = ByteBuffer.wrap(bytes);
	}

	public static RegionFileData read(Path path) throws IOException {
		return new RegionFileData(Files.readAllBytes(path));
	}

	public static RegionFileData of(byte[] bytes) {
		return new RegionFileData(bytes);
	}

	/**
	 * Returns the region coordinates encoded in a file name like {@code r.-1.2.mca}, or null if the name does not match.
	 */
	public static int[] parseRegionCoordinates(String fileName) {
		Matcher matcher = FILE_NAME.matcher(fileName);
		if (!matcher.matches()) {
			return null;
		}
		return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
	}

	public boolean hasValidHeader() {
		return this.bytes.length >= HEADER_BYTES;
	}

	public int size() {
		return this.bytes.length;
	}

	/**
	 * Whether the entry exists and its sectors and length field point inside the file.
	 */
	public boolean hasEntry(int index) {
		if (!this.hasValidHeader()) {
			return false;
		}
		int location = this.buffer.getInt(index * 4);
		int sectorOffset = location >>> 8;
		int sectorCount = location & 0xFF;
		if (sectorOffset < 2 || sectorCount == 0) {
			return false;
		}
		long start = (long) sectorOffset * SECTOR_BYTES;
		if (start + ENTRY_HEADER_BYTES > this.bytes.length) {
			return false;
		}
		int length = this.buffer.getInt((int) start);
		return length >= 1 && start + 4 + length <= this.bytes.length && length + 4 <= (long) sectorCount * SECTOR_BYTES;
	}

	public BitSet presentEntries() {
		BitSet present = new BitSet(ENTRY_COUNT);
		for (int i = 0; i < ENTRY_COUNT; i++) {
			if (this.hasEntry(i)) {
				present.set(i);
			}
		}
		return present;
	}

	private int entryStart(int index) {
		return (this.buffer.getInt(index * 4) >>> 8) * SECTOR_BYTES;
	}

	private int entryLength(int index) {
		return this.buffer.getInt(this.entryStart(index));
	}

	/**
	 * Raw compression byte of the entry, including the external (.mcc) flag.
	 */
	public int compressionByte(int index) {
		return this.bytes[this.entryStart(index) + 4] & 0xFF;
	}

	public boolean isExternal(int index) {
		return (this.compressionByte(index) & EXTERNAL_FLAG) != 0;
	}

	/**
	 * Compressed payload of the entry, without the length and compression header.
	 */
	public InputStream payload(int index) {
		int start = this.entryStart(index);
		return new ByteArrayInputStream(this.bytes, start + ENTRY_HEADER_BYTES, this.entryLength(index) - 1);
	}

	/**
	 * Builds a new region file that only contains the entries in {@code keep} that exist in this file.
	 */
	public byte[] filter(BitSet keep) {
		int dataBytes = 0;
		for (int i = keep.nextSetBit(0); i >= 0 && i < ENTRY_COUNT; i = keep.nextSetBit(i + 1)) {
			if (this.hasEntry(i)) {
				dataBytes += sectorsFor(this.entryLength(i) + 4) * SECTOR_BYTES;
			}
		}

		byte[] output = new byte[HEADER_BYTES + dataBytes];
		ByteBuffer out = ByteBuffer.wrap(output);
		int nextSector = 2;
		for (int i = keep.nextSetBit(0); i >= 0 && i < ENTRY_COUNT; i = keep.nextSetBit(i + 1)) {
			if (!this.hasEntry(i)) {
				continue;
			}
			int totalBytes = this.entryLength(i) + 4;
			int sectors = sectorsFor(totalBytes);
			out.putInt(i * 4, nextSector << 8 | sectors);
			out.putInt(SECTOR_BYTES + i * 4, this.buffer.getInt(SECTOR_BYTES + i * 4));
			System.arraycopy(this.bytes, this.entryStart(i), output, nextSector * SECTOR_BYTES, totalBytes);
			nextSector += sectors;
		}
		return output;
	}

	private static int sectorsFor(int bytes) {
		return (bytes + SECTOR_BYTES - 1) / SECTOR_BYTES;
	}
}
