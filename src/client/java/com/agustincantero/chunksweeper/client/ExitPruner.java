package com.agustincantero.chunksweeper.client;

import com.agustincantero.chunksweeper.ChunkSweeperConfig;
import com.agustincantero.chunksweeper.ChunkSweeper;
import com.agustincantero.chunksweeper.prune.PruneProgress;
import com.agustincantero.chunksweeper.prune.PruneResult;
import com.agustincantero.chunksweeper.prune.WorldPruner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jspecify.annotations.Nullable;

/**
 * Prunes a singleplayer world right after the integrated server has saved and closed it.
 */
public final class ExitPruner {
	private static volatile @Nullable PruneProgress running;
	private static volatile @Nullable PruneResult summary;

	private ExitPruner() {
	}

	/**
	 * Progress of the prune that is running while the "Saving world" screen is shown, or null if none is running.
	 */
	public static @Nullable PruneProgress running() {
		return running;
	}

	/**
	 * Result of the prune that just finished while its summary is on screen, or null.
	 */
	public static @Nullable PruneResult summary() {
		return summary;
	}

	/**
	 * Runs on the server thread, after the level storage lock has been released. The client keeps rendering the saving screen until this returns.
	 */
	public static void prune(String levelId) {
		ChunkSweeperConfig config = ChunkSweeperConfig.get();
		if (!config.pruneOnWorldExit) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		PruneProgress progress = new PruneProgress();
		running = progress;
		PruneResult result = null;
		// Taking the lock again keeps the world from being opened or edited while its files are rewritten.
		try (LevelStorageAccess access = minecraft.getLevelSource().validateAndCreateAccess(levelId)) {
			long start = System.nanoTime();
			result = new WorldPruner(config.inhabitedTimeThreshold).pruneInPlace(access.getLevelPath(LevelResource.ROOT), progress);
			ChunkSweeper.LOGGER.info("Removed {} unused chunks from {} ({} bytes freed) in {} ms", result.removedChunks(), levelId, result.bytesFreed(), (System.nanoTime() - start) / 1_000_000);
		} catch (Exception e) {
			ChunkSweeper.LOGGER.error("Could not remove unused chunks from {}", levelId, e);
			minecraft.execute(() -> Toasts.showPrune(minecraft, Component.translatable("chunksweeper.prune.failed.title"), Component.literal(String.valueOf(e.getMessage()))));
		} finally {
			running = null;
		}

		if (result == null || result.removedChunks() == 0) {
			return;
		}
		PruneResult finished = result;
		showSummary(finished, config.exitSummarySeconds);
		minecraft.execute(() -> Toasts.showPrune(
			minecraft,
			Component.translatable("chunksweeper.prune.done.title"),
			Component.translatable("chunksweeper.prune.done.message", finished.removedChunks(), Toasts.toMegabytes(finished.bytesFreed()))
		));
	}

	// Holding the server thread keeps the client on the saving screen, which is the only screen shown between the world and the title screen.
	private static void showSummary(PruneResult result, double seconds) {
		if (seconds <= 0) {
			return;
		}
		summary = result;
		try {
			Thread.sleep((long) (seconds * 1000));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			summary = null;
		}
	}
}
