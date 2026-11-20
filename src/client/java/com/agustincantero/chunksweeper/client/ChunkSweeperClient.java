package com.agustincantero.chunksweeper.client;

import com.agustincantero.chunksweeper.ChunkSweeperConfig;
import net.fabricmc.api.ClientModInitializer;

public final class ChunkSweeperClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ChunkSweeperConfig.load();
	}
}
