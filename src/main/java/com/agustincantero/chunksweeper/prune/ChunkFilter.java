package com.agustincantero.chunksweeper.prune;

import com.agustincantero.chunksweeper.ChunkSweeper;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.BitSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

/**
 * Decides which chunks of a region file are kept, the same way Thanos does with InhabitedTimePattern and ForceLoadedChunkPatternFactory.
 */
public final class ChunkFilter {
	private static final String INHABITED_TIME = "InhabitedTime";

	private final long inhabitedTimeThreshold;

	/**
	 * @param inhabitedTimeThreshold chunks are kept when their InhabitedTime (in ticks) is greater than this value
	 */
	public ChunkFilter(long inhabitedTimeThreshold) {
		this.inhabitedTimeThreshold = inhabitedTimeThreshold;
	}

	/**
	 * Returns the indexes of the entries in {@code region} that must be kept.
	 */
	public BitSet keptEntries(RegionFileData region, int regionX, int regionZ, LongSet forcedChunks) {
		BitSet kept = new BitSet(RegionFileData.ENTRY_COUNT);
		for (int i = 0; i < RegionFileData.ENTRY_COUNT; i++) {
			if (!region.hasEntry(i)) {
				continue;
			}
			int chunkX = regionX * 32 + (i & 31);
			int chunkZ = regionZ * 32 + (i >> 5);
			if (forcedChunks.contains(ForcedChunks.pack(chunkX, chunkZ)) || this.isInhabited(region, i, chunkX, chunkZ)) {
				kept.set(i);
			}
		}
		return kept;
	}

	// Anything that cannot be read is kept, so a parsing problem never deletes a chunk.
	private boolean isInhabited(RegionFileData region, int index, int chunkX, int chunkZ) {
		if (region.isExternal(index)) {
			return true;
		}
		RegionFileVersion version = RegionFileVersion.fromId(region.compressionByte(index));
		if (version == null || version == RegionFileVersion.VERSION_CUSTOM) {
			return true;
		}

		CollectFields collector = new CollectFields(new FieldSelector(LongTag.TYPE, INHABITED_TIME));
		try (InputStream in = version.wrap(region.payload(index))) {
			NbtIo.parse(new DataInputStream(in), collector, NbtAccounter.uncompressedQuota());
		} catch (Exception e) {
			ChunkSweeper.LOGGER.warn("Could not read chunk [{}, {}], keeping it", chunkX, chunkZ, e);
			return true;
		}

		if (!(collector.getResult() instanceof CompoundTag root)) {
			return true;
		}
		return root.getLong(INHABITED_TIME).map(time -> time > this.inhabitedTimeThreshold).orElse(true);
	}
}
