package com.agustincantero.chunksweeper.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Adds the "Configure" button in Mod Menu. Only loaded when Mod Menu is installed, since it is declared as a "modmenu" entrypoint.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ChunkSweeperConfigScreen::new;
	}
}
