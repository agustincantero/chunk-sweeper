package com.agustincantero.chunksweeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings stored in {@code config/chunksweeper.json}. Missing fields keep their default values.
 */
public final class ChunkSweeperConfig {
	public static final int MAX_EXIT_SUMMARY_SECONDS = 10;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ChunkSweeperConfig instance = new ChunkSweeperConfig();

	/**
	 * Remove unused chunks from a singleplayer world after it is saved and closed.
	 */
	public boolean pruneOnWorldExit = true;

	/**
	 * How long the summary stays on the "Saving world" screen after chunks were removed on exit. 0 disables it.
	 */
	public int exitSummarySeconds = 2;

	/**
	 * The "Make Backup" button replaces a single {@code <world folder>.zip} instead of adding a dated zip every time.
	 */
	public boolean overwriteBackups = false;

	/**
	 * Chunks are kept when their InhabitedTime (in ticks, 20 per second) is greater than this value.
	 */
	public long inhabitedTimeThreshold = 0;

	public static ChunkSweeperConfig get() {
		return instance;
	}

	public static void load() {
		load(defaultFile());
	}

	static void load(Path file) {
		instance = new ChunkSweeperConfig();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				ChunkSweeperConfig loaded = GSON.fromJson(reader, ChunkSweeperConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				ChunkSweeper.LOGGER.error("Could not read {}, using defaults", file, e);
				return;
			}
		}
		save(file);
	}

	public static void save() {
		save(defaultFile());
	}

	static void save(Path file) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			ChunkSweeper.LOGGER.error("Could not write {}", file, e);
		}
	}

	private static Path defaultFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("chunksweeper.json");
	}
}
