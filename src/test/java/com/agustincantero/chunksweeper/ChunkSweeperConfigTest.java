package com.agustincantero.chunksweeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkSweeperConfigTest {
	@TempDir
	Path temp;

	@Test
	void createsDefaultConfigWhenFileDoesNotExist() {
		Path file = this.temp.resolve("config/chunksweeper.json");

		ChunkSweeperConfig.load(file);
		ChunkSweeperConfig config = ChunkSweeperConfig.get();

		assertTrue(config.pruneOnWorldExit);
		assertEquals(2, config.exitSummarySeconds);
		assertFalse(config.overwriteBackups);
		assertEquals(0L, config.inhabitedTimeThreshold);
		assertTrue(Files.isRegularFile(file));
	}

	@Test
	void loadsExistingSettingsAndFillsMissingFields() throws IOException {
		Path file = this.temp.resolve("chunksweeper.json");
		Files.writeString(file, "{\"pruneOnWorldExit\": false, \"inhabitedTimeThreshold\": 200}");

		ChunkSweeperConfig.load(file);
		ChunkSweeperConfig config = ChunkSweeperConfig.get();

		assertFalse(config.pruneOnWorldExit);
		assertEquals(2, config.exitSummarySeconds);
		assertFalse(config.overwriteBackups);
		assertEquals(200L, config.inhabitedTimeThreshold);

		String saved = Files.readString(file);
		assertTrue(saved.contains("exitSummarySeconds"));
		assertTrue(saved.contains("overwriteBackups"));
	}

	@Test
	void keepsBrokenConfigFileUntouchedAndUsesDefaults() throws IOException {
		Path file = this.temp.resolve("chunksweeper.json");
		String broken = "{\"pruneOnWorldExit\": false, broken";
		Files.writeString(file, broken);

		ChunkSweeperConfig.load(file);
		ChunkSweeperConfig config = ChunkSweeperConfig.get();

		assertTrue(config.pruneOnWorldExit);
		assertEquals(2, config.exitSummarySeconds);
		assertFalse(config.overwriteBackups);
		assertEquals(0L, config.inhabitedTimeThreshold);
		assertEquals(broken, Files.readString(file));
	}
}
