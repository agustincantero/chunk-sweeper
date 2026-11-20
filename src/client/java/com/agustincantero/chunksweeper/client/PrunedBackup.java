package com.agustincantero.chunksweeper.client;

import com.agustincantero.chunksweeper.ChunkSweeperConfig;
import com.agustincantero.chunksweeper.ChunkSweeper;
import com.agustincantero.chunksweeper.prune.PruneProgress;
import com.agustincantero.chunksweeper.prune.PruneResult;
import com.agustincantero.chunksweeper.prune.WorldPruner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.FileNameDateFormatter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;

/**
 * Replacement for {@code EditWorldScreen.makeBackupAndShowToast} that writes the zip with unused chunks left out.
 * Same file name, folder and toasts as the vanilla backup.
 */
public final class PrunedBackup {
	private PrunedBackup() {
	}

	public static CompletableFuture<Boolean> makeBackupAndShowToast(LevelStorageAccess access) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.setScreenAndShow(
			GenericWaitingScreen.createWaitingWithoutButton(
				Component.translatable("selectWorld.waitingForBackup.title"),
				Component.translatable("selectWorld.waitingForBackup.message").withStyle(ChatFormatting.GRAY)
			)
		);
		return CompletableFuture.supplyAsync(() -> {
			try {
				return makeBackup(minecraft, access);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, Util.backgroundExecutor()).thenApplyAsync(backup -> {
			Component title = Component.translatable("selectWorld.edit.backupCreated", access.getLevelId());
			String key = backup.replaced() ? "chunksweeper.backup.replaced" : "chunksweeper.backup.created";
			Component message = Component.translatable(key, Toasts.toMegabytes(backup.zipSize()), backup.result().removedChunks());
			Toasts.show(minecraft, SystemToast.SystemToastId.WORLD_BACKUP, title, message);
			return true;
		}, minecraft).exceptionallyAsync(exception -> {
			ChunkSweeper.LOGGER.error("Could not create pruned backup of {}", access.getLevelId(), exception);
			Component title = Component.translatable("selectWorld.edit.backupFailed");
			Toasts.show(minecraft, SystemToast.SystemToastId.WORLD_BACKUP, title, Component.literal(String.valueOf(exception.getMessage())));
			return false;
		}, minecraft);
	}

	private static Backup makeBackup(Minecraft minecraft, LevelStorageAccess access) throws IOException {
		Path backups = minecraft.getLevelSource().getBackupPath();
		FileUtil.createDirectoriesSafe(backups);
		boolean overwrite = ChunkSweeperConfig.get().overwriteBackups;
		Path zip;
		if (overwrite) {
			zip = backups.resolve(access.getLevelId() + ".zip");
		} else {
			String prefix = FileNameDateFormatter.FORMATTER.format(ZonedDateTime.now()) + "_" + access.getLevelId();
			zip = backups.resolve(FileUtil.findAvailableName(backups, prefix, ".zip"));
		}
		boolean replaced = overwrite && Files.exists(zip);
		// When replacing, the new zip is written next to the old one first, so a failed backup never leaves the player without one.
		Path target = overwrite ? zip.resolveSibling(zip.getFileName() + ".tmp") : zip;

		long start = System.nanoTime();
		PruneResult result;
		try {
			result = new WorldPruner(ChunkSweeperConfig.get().inhabitedTimeThreshold)
				.writePrunedBackup(access.getLevelPath(LevelResource.ROOT), access.getLevelId(), target, new PruneProgress());
			if (overwrite) {
				moveReplacing(target, zip);
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(target);
			throw e;
		}
		ChunkSweeper.LOGGER.info("Created pruned backup {} ({} unused chunks left out) in {} ms", zip.getFileName(), result.removedChunks(), (System.nanoTime() - start) / 1_000_000);
		return new Backup(Files.size(zip), result, replaced);
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private record Backup(long zipSize, PruneResult result, boolean replaced) {
	}
}
