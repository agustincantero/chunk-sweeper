package com.agustincantero.chunksweeper.prune;

import com.agustincantero.chunksweeper.ChunkSweeper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Reads the chunks that a dimension keeps loaded through saved chunk tickets (for example {@code /forceload}).
 */
public final class ForcedChunks {
	private static final String TICKETS_FILE = "data/minecraft/chunk_tickets.dat";
	private static final String LEGACY_FILE = "data/chunks.dat";

	private ForcedChunks() {
	}

	public static long pack(int chunkX, int chunkZ) {
		return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
	}

	/**
	 * Returns the packed positions of every chunk with a saved ticket in the dimension, or an empty set if none can be read.
	 */
	public static LongSet read(Path dimension) {
		LongSet chunks = new LongOpenHashSet();
		Path tickets = dimension.resolve(TICKETS_FILE);
		Path legacy = dimension.resolve(LEGACY_FILE);
		try {
			if (Files.isRegularFile(tickets)) {
				readTickets(NbtIo.readCompressed(tickets, NbtAccounter.uncompressedQuota()), chunks);
			} else if (Files.isRegularFile(legacy)) {
				readLegacy(NbtIo.readCompressed(legacy, NbtAccounter.uncompressedQuota()), chunks);
			}
		} catch (Exception e) {
			ChunkSweeper.LOGGER.warn("Could not read force-loaded chunks of {}", dimension, e);
		}
		return chunks;
	}

	// Every saved ticket is kept, not only "minecraft:forced", since a ticket that survives a restart means the chunk is meant to stay loaded.
	private static void readTickets(CompoundTag root, LongSet chunks) {
		for (Tag entry : root.getCompoundOrEmpty("data").getListOrEmpty("tickets")) {
			if (entry instanceof CompoundTag ticket) {
				ticket.getIntArray("chunk_pos")
					.filter(pos -> pos.length == 2)
					.ifPresent(pos -> chunks.add(pack(pos[0], pos[1])));
			}
		}
	}

	private static void readLegacy(CompoundTag root, LongSet chunks) {
		root.getCompoundOrEmpty("data").getLongArray("Forced").ifPresent(packed -> {
			for (long pos : packed) {
				chunks.add(pack((int) pos, (int) (pos >>> 32)));
			}
		});
	}
}
