package com.agustincantero.chunksweeper.prune;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Removes unused chunks from a world folder, either in place or while writing a zip backup.
 * Like Thanos, any folder that contains a {@code region}, {@code entities} or {@code poi} folder is treated as a dimension, which covers both the old layout ({@code DIM-1}) and the 26.1+ layout ({@code dimensions/<namespace>/<name>}).
 */
public final class WorldPruner {
	private static final String REGION = "region";
	private static final List<String> STORAGE_FOLDERS = List.of(REGION, "entities", "poi");
	private static final String TEMP_SUFFIX = ".chunksweeper-tmp";
	private static final String SESSION_LOCK = "session.lock";

	private final ChunkFilter filter;
	private final boolean keepForcedChunks;

	public WorldPruner(long inhabitedTimeThreshold, boolean keepForcedChunks) {
		this.filter = new ChunkFilter(inhabitedTimeThreshold);
		this.keepForcedChunks = keepForcedChunks;
	}

	public WorldPruner(long inhabitedTimeThreshold) {
		this(inhabitedTimeThreshold, true);
	}

	/**
	 * Prunes the world folder in place. Each file is written to a temporary file first and then moved over the original, so an interrupted run leaves every file either untouched or fully pruned.
	 * The world must not be open while this runs.
	 */
	public PruneResult pruneInPlace(Path worldRoot, PruneProgress progress) throws IOException {
		List<RegionJob> jobs = findRegions(worldRoot);
		progress.start(jobs.size());
		Map<Path, LongSet> forcedCache = new HashMap<>();
		Totals totals = new Totals();

		for (RegionJob job : jobs) {
			RegionFileData region = RegionFileData.read(job.file(REGION));
			BitSet kept = this.keptEntries(job, region, forcedCache, totals, progress);
			if (kept != null) {
				for (String folder : STORAGE_FOLDERS) {
					Path file = job.file(folder);
					if (!Files.isRegularFile(file)) {
						continue;
					}
					RegionFileData data = folder.equals(REGION) ? region : RegionFileData.read(file);
					if (!data.hasValidHeader()) {
						continue;
					}
					totals.bytesBefore += data.size();
					if (!data.presentEntries().intersects(kept)) {
						Files.delete(file);
						progress.addBytesFreed(data.size());
					} else {
						byte[] pruned = data.filter(kept);
						totals.bytesAfter += pruned.length;
						replace(file, pruned);
						progress.addBytesFreed(data.size() - pruned.length);
					}
				}
			}
			progress.step();
		}
		return totals.result();
	}

	/**
	 * Writes a zip backup of the world with unused chunks left out, using the same entry layout as the vanilla backup ({@code <rootName>/<relative path>}). The world folder is not modified.
	 */
	public PruneResult writePrunedBackup(Path worldRoot, String rootName, Path zipFile, PruneProgress progress) throws IOException {
		List<RegionJob> jobs = findRegions(worldRoot);
		List<Path> files;
		try (Stream<Path> stream = Files.walk(worldRoot)) {
			files = stream.filter(Files::isRegularFile).toList();
		}
		progress.start(jobs.size() + files.size());
		Map<Path, LongSet> forcedCache = new HashMap<>();
		Totals totals = new Totals();

		Map<Path, BitSet> keptByFile = new HashMap<>();
		for (RegionJob job : jobs) {
			BitSet kept = this.keptEntries(job, RegionFileData.read(job.file(REGION)), forcedCache, totals, progress);
			if (kept != null) {
				for (String folder : STORAGE_FOLDERS) {
					keptByFile.put(job.file(folder).toAbsolutePath().normalize(), kept);
				}
			}
			progress.step();
		}

		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (name.equals(SESSION_LOCK) || name.endsWith(TEMP_SUFFIX)) {
					progress.step();
					continue;
				}

				BitSet kept = keptByFile.get(file.toAbsolutePath().normalize());
				byte[] pruned = null;
				if (kept != null) {
					RegionFileData data = RegionFileData.read(file);
					if (data.hasValidHeader()) {
						totals.bytesBefore += data.size();
						if (!data.presentEntries().intersects(kept)) {
							progress.step();
							continue;
						}
						pruned = data.filter(kept);
						totals.bytesAfter += pruned.length;
					}
				}

				String entryName = rootName + "/" + worldRoot.relativize(file).toString().replace('\\', '/');
				zip.putNextEntry(new ZipEntry(entryName));
				if (pruned != null) {
					zip.write(pruned);
				} else {
					Files.copy(file, zip);
				}
				zip.closeEntry();
				progress.step();
			}
		}
		return totals.result();
	}

	/**
	 * Returns the entries to keep, or null when the region file does not need to change.
	 */
	private BitSet keptEntries(RegionJob job, RegionFileData region, Map<Path, LongSet> forcedCache, Totals totals, PruneProgress progress) {
		if (!region.hasValidHeader()) {
			return null;
		}
		LongSet forced = this.keepForcedChunks ? forcedCache.computeIfAbsent(job.dimension(), ForcedChunks::read) : LongSets.EMPTY_SET;
		BitSet present = region.presentEntries();
		BitSet kept = this.filter.keptEntries(region, job.regionX(), job.regionZ(), forced);
		int removed = present.cardinality() - kept.cardinality();
		totals.removedChunks += removed;
		progress.addRemovedChunks(removed);
		totals.keptChunks += kept.cardinality();
		return kept.equals(present) ? null : kept;
	}

	public static List<Path> findDimensions(Path worldRoot) throws IOException {
		try (Stream<Path> stream = Files.walk(worldRoot)) {
			return stream
				.filter(Files::isDirectory)
				.filter(dir -> STORAGE_FOLDERS.stream().anyMatch(folder -> Files.isDirectory(dir.resolve(folder))))
				.toList();
		}
	}

	private static List<RegionJob> findRegions(Path worldRoot) throws IOException {
		List<RegionJob> jobs = new ArrayList<>();
		for (Path dimension : findDimensions(worldRoot)) {
			Path regionFolder = dimension.resolve(REGION);
			if (!Files.isDirectory(regionFolder)) {
				continue;
			}
			try (Stream<Path> stream = Files.list(regionFolder)) {
				for (Path file : stream.toList()) {
					String name = file.getFileName().toString();
					int[] coordinates = RegionFileData.parseRegionCoordinates(name);
					if (coordinates != null && Files.isRegularFile(file) && Files.size(file) > 0) {
						jobs.add(new RegionJob(dimension, name, coordinates[0], coordinates[1]));
					}
				}
			}
		}
		return jobs;
	}

	private static void replace(Path target, byte[] bytes) throws IOException {
		Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
		Files.write(temp, bytes);
		try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private record RegionJob(Path dimension, String fileName, int regionX, int regionZ) {
		Path file(String folder) {
			return this.dimension.resolve(folder).resolve(this.fileName);
		}
	}

	private static final class Totals {
		int removedChunks;
		int keptChunks;
		long bytesBefore;
		long bytesAfter;

		PruneResult result() {
			return new PruneResult(this.removedChunks, this.keptChunks, this.bytesBefore, this.bytesAfter);
		}
	}
}
