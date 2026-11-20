package com.agustincantero.chunksweeper.prune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldPrunerTest {
	// Expected value taken from ThanosTest::testSnap in the PHP library, which uses the same world without force-loaded chunks.
	private static final int THANOS_REMOVED_CHUNKS = 3660;

	@TempDir
	Path temp;

	@Test
	void prunesTheSameChunksAsThanos() throws IOException {
		Path world = this.copyFixture("world");
		PruneResult result = new WorldPruner(0, false).pruneInPlace(world, new PruneProgress());

		assertEquals(THANOS_REMOVED_CHUNKS, result.removedChunks());
		assertTrue(result.bytesAfter() < result.bytesBefore());
	}

	@Test
	void pruningTwiceRemovesNothingTheSecondTime() throws IOException {
		Path world = this.copyFixture("world");
		new WorldPruner(0, false).pruneInPlace(world, new PruneProgress());
		PruneResult second = new WorldPruner(0, false).pruneInPlace(world, new PruneProgress());

		assertEquals(0, second.removedChunks());
		assertEquals(0, second.bytesBefore());
	}

	@Test
	void keptChunksAreStillReadable() throws IOException {
		Path world = this.copyFixture("world");
		PruneResult result = new WorldPruner(0, false).pruneInPlace(world, new PruneProgress());

		int readable = 0;
		for (Path region : this.regionFiles(world)) {
			RegionFileData data = RegionFileData.read(region);
			int[] coordinates = RegionFileData.parseRegionCoordinates(region.getFileName().toString());
			BitSet present = data.presentEntries();
			assertEquals(present, new ChunkFilter(0).keptEntries(data, coordinates[0], coordinates[1], new LongOpenHashSet()));
			readable += present.cardinality();
		}
		assertEquals(result.keptChunks(), readable);
	}

	@Test
	void entitiesAndPoiFollowTheRegionFile() throws IOException {
		Path world = this.copyFixture("world");
		new WorldPruner(0, false).pruneInPlace(world, new PruneProgress());

		for (String folder : new String[]{"entities", "poi"}) {
			Path file = world.resolve(folder).resolve("r.0.0.mca");
			if (!Files.exists(file)) {
				continue;
			}
			BitSet region = RegionFileData.read(world.resolve("region/r.0.0.mca")).presentEntries();
			BitSet other = RegionFileData.read(file).presentEntries();
			other.andNot(region);
			assertTrue(other.isEmpty(), folder + " keeps entries for removed chunks");
		}
	}

	@Test
	void forceLoadedChunksAreKept() throws IOException {
		Path world = this.copyFixture("world-26-1");
		Path overworld = world.resolve("dimensions/minecraft/overworld");
		new WorldPruner(Long.MAX_VALUE, true).pruneInPlace(world, new PruneProgress());

		BitSet present = RegionFileData.read(overworld.resolve("region/r.0.0.mca")).presentEntries();
		BitSet expected = new BitSet();
		expected.set(0);
		expected.set(1);
		expected.set(32);
		expected.set(33);
		assertEquals(expected, present);
	}

	@Test
	void readsForcedChunksFrom26dot1Tickets() throws IOException {
		LongSet chunks = ForcedChunks.read(this.copyFixture("world-26-1").resolve("dimensions/minecraft/overworld"));
		assertEquals(Set.of(ForcedChunks.pack(0, 0), ForcedChunks.pack(0, 1), ForcedChunks.pack(1, 0), ForcedChunks.pack(1, 1)), new HashSet<>(chunks));
	}

	@Test
	void readsLegacyForcedChunks() throws IOException {
		LongSet chunks = ForcedChunks.read(this.copyFixture("legacy-forceload"));
		Set<Long> expected = new HashSet<>();
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				expected.add(ForcedChunks.pack(x, z));
			}
		}
		assertEquals(expected, new HashSet<>(chunks));
	}

	@Test
	void backupLeavesTheWorldUntouchedAndPrunesTheZip() throws IOException {
		Path world = this.copyFixture("world");
		long sizeBefore = this.totalSize(world);
		Path zip = this.temp.resolve("backup.zip");
		PruneResult result = new WorldPruner(0, false).writePrunedBackup(world, "world", zip, new PruneProgress());

		assertEquals(THANOS_REMOVED_CHUNKS, result.removedChunks());
		assertEquals(sizeBefore, this.totalSize(world));

		Path extracted = this.temp.resolve("extracted");
		this.unzip(zip, extracted);
		assertFalse(Files.exists(extracted.resolve("world/session.lock")));
		assertTrue(Files.exists(extracted.resolve("world/level.dat")));
		assertTrue(Files.exists(extracted.resolve("world/region/other-file.txt")));

		PruneResult again = new WorldPruner(0, false).pruneInPlace(extracted.resolve("world"), new PruneProgress());
		assertEquals(0, again.removedChunks());
		assertEquals(result.keptChunks(), again.keptChunks());
	}

	private Path copyFixture(String name) throws IOException {
		// Extracted by the extractTestWorlds Gradle task.
		Path source = Path.of(System.getProperty("chunksweeper.testWorlds")).resolve(name);
		Path target = this.temp.resolve(name);
		try (Stream<Path> stream = Files.walk(source)) {
			for (Path path : stream.toList()) {
				Path destination = target.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.copy(path, destination);
				}
			}
		}
		return target;
	}

	private Iterable<Path> regionFiles(Path world) throws IOException {
		try (Stream<Path> stream = Files.walk(world)) {
			return stream.filter(path -> path.getParent().getFileName().toString().equals("region"))
				.filter(path -> RegionFileData.parseRegionCoordinates(path.getFileName().toString()) != null)
				.toList();
		}
	}

	private long totalSize(Path root) throws IOException {
		try (Stream<Path> stream = Files.walk(root)) {
			long size = 0;
			for (Path path : stream.filter(Files::isRegularFile).toList()) {
				size += Files.size(path);
			}
			return size;
		}
	}

	private void unzip(Path zip, Path target) throws IOException {
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				Path file = target.resolve(entry.getName());
				Files.createDirectories(file.getParent());
				Files.copy(in, file);
			}
		}
	}
}
