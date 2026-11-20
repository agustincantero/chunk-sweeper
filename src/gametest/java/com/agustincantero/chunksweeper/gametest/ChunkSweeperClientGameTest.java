package com.agustincantero.chunksweeper.gametest;

import com.agustincantero.chunksweeper.ChunkSweeperConfig;
import com.agustincantero.chunksweeper.client.ExitPruner;
import com.agustincantero.chunksweeper.client.SavingScreen;
import com.agustincantero.chunksweeper.prune.PruneProgress;
import com.agustincantero.chunksweeper.prune.PruneResult;
import com.agustincantero.chunksweeper.prune.RegionFileData;
import com.agustincantero.chunksweeper.prune.WorldPruner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;

/**
 * Plays a real singleplayer world and checks both the pruned "Make Backup" button and the prune that runs when the world is closed.
 */
public final class ChunkSweeperClientGameTest implements FabricClientGameTest {
	// Far from the player and never visited, so its InhabitedTime stays 0 and only the forceload ticket keeps it.
	private static final int FORCED_CHUNK_X = 300;
	private static final int FORCED_CHUNK_Z = 300;

	@Override
	public void runTest(ClientGameTestContext context) {
		ChunkSweeperConfig.get().pruneOnWorldExit = false;

		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			world.getServer().runCommand("forceload add " + FORCED_CHUNK_X * 16 + " " + FORCED_CHUNK_Z * 16);
			world.getServer().runCommand("tp @a 3000 100 3000");
			context.waitTicks(200);
		}

		Path worldDir = save.getSaveDirectory();
		int chunksBefore = countChunks(worldDir);
		check(chunksBefore > 0, "the world has no saved chunks");
		check(hasChunk(worldDir, FORCED_CHUNK_X, FORCED_CHUNK_Z), "the force-loaded chunk was not saved");

		Path backups = context.computeOnClient(minecraft -> minecraft.getLevelSource().getBackupPath());
		List<Path> backupsBefore = listZips(backups);
		String levelId = worldDir.getFileName().toString();
		clickBackup(context, levelId);
		context.waitFor(minecraft -> listZips(backups).size() > backupsBefore.size(), 20 * 60);
		context.waitForScreen(TitleScreen.class);

		Path zip = listZips(backups).stream().filter(path -> !backupsBefore.contains(path)).findFirst().orElseThrow();
		int chunksInBackup = countChunksInZip(zip);
		check(countChunks(worldDir) == chunksBefore, "making a backup changed the world");
		check(chunksInBackup > 0 && chunksInBackup < chunksBefore, "the backup was not pruned: " + chunksInBackup + " of " + chunksBefore + " chunks");

		// With overwriteBackups two backups in a row leave a single undated zip and no temporary file behind.
		ChunkSweeperConfig.get().overwriteBackups = true;
		clickBackup(context, levelId);
		context.waitForScreen(TitleScreen.class);
		clickBackup(context, levelId);
		context.waitForScreen(TitleScreen.class);
		Path single = backups.resolve(levelId + ".zip");
		check(Files.exists(single), "the overwritten backup " + single.getFileName() + " was not created");
		check(!Files.exists(backups.resolve(levelId + ".zip.tmp")), "the temporary backup file was left behind");
		check(listZips(backups).size() == backupsBefore.size() + 2, "replacing the backup created extra zips: " + listZips(backups));
		check(countChunksInZip(single) == chunksInBackup, "the overwritten backup has a different number of chunks");
		ChunkSweeperConfig.get().overwriteBackups = false;

		ChunkSweeperConfig.get().pruneOnWorldExit = true;
		try (TestSingleplayerContext world = save.open()) {
			context.waitTicks(20);
		}
		context.waitForScreen(TitleScreen.class);

		int chunksAfter = countChunks(worldDir);
		check(chunksAfter < chunksBefore, "closing the world did not prune it: " + chunksAfter + " of " + chunksBefore + " chunks");
		check(hasChunk(worldDir, FORCED_CHUNK_X, FORCED_CHUNK_Z), "the force-loaded chunk was removed");
		PruneResult again = prune(worldDir);
		check(again.removedChunks() == 0, "the pruned world still had " + again.removedChunks() + " unused chunks");

		this.screenshotExitSummary(context);
	}

	// The real exit prune is too fast to capture, so a second world is pruned by hand while the saving screen is open.
	private void screenshotExitSummary(ClientGameTestContext context) {
		ChunkSweeperConfig.get().pruneOnWorldExit = false;
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			world.getServer().runCommand("tp @a -3000 100 -3000");
			context.waitTicks(100);
		}
		context.waitForScreen(TitleScreen.class);

		ChunkSweeperConfig.get().pruneOnWorldExit = true;
		String levelId = save.getSaveDirectory().getFileName().toString();
		context.setScreen(SavingScreen::new);
		Thread pruner = new Thread(() -> ExitPruner.prune(levelId), "chunksweeper-gametest-prune");
		pruner.start();
		context.waitFor(minecraft -> ExitPruner.summary() != null);
		context.waitTicks(5);
		context.takeScreenshot("chunksweeper-exit-summary");
		try {
			pruner.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		context.setScreen(TitleScreen::new);
	}

	private static void clickBackup(ClientGameTestContext context, String levelId) {
		context.runOnClient(minecraft -> {
			try {
				LevelStorageAccess access = minecraft.getLevelSource().validateAndCreateAccess(levelId);
				minecraft.gui.setScreen(EditWorldScreen.create(minecraft, access, changed -> {
					try {
						access.close();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
					minecraft.gui.setScreen(new TitleScreen());
				}));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		context.waitForScreen(EditWorldScreen.class);
		context.clickScreenButton("selectWorld.edit.backup");
	}

	private static PruneResult prune(Path worldDir) {
		try {
			return new WorldPruner(0).pruneInPlace(worldDir, new PruneProgress());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static int countChunks(Path worldDir) {
		int count = 0;
		for (Path region : regionFiles(worldDir)) {
			try {
				count += RegionFileData.read(region).presentEntries().cardinality();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return count;
	}

	private static boolean hasChunk(Path worldDir, int chunkX, int chunkZ) {
		Path region = worldDir.resolve("dimensions/minecraft/overworld/region/r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca");
		try {
			return Files.exists(region) && RegionFileData.read(region).hasEntry(Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<Path> regionFiles(Path worldDir) {
		try (Stream<Path> stream = Files.walk(worldDir)) {
			return stream
				.filter(path -> path.getParent().getFileName().toString().equals("region"))
				.filter(path -> RegionFileData.parseRegionCoordinates(path.getFileName().toString()) != null)
				.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static int countChunksInZip(Path zip) {
		int count = 0;
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				String[] parts = entry.getName().split("/");
				if (parts.length >= 2 && parts[parts.length - 2].equals("region") && RegionFileData.parseRegionCoordinates(parts[parts.length - 1]) != null) {
					count += RegionFileData.of(in.readAllBytes()).presentEntries().cardinality();
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return count;
	}

	private static List<Path> listZips(Path folder) {
		if (!Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(folder)) {
			return stream.filter(path -> path.toString().endsWith(".zip")).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
