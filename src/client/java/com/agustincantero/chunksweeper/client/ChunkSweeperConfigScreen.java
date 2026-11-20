package com.agustincantero.chunksweeper.client;

import com.agustincantero.chunksweeper.ChunkSweeperConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Settings screen built from the same widgets as the vanilla options screens. Opened from Mod Menu.
 */
public final class ChunkSweeperConfigScreen extends OptionsSubScreen {
	public ChunkSweeperConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("chunksweeper.config.title"));
	}

	@Override
	protected void addOptions() {
		ChunkSweeperConfig config = ChunkSweeperConfig.get();

		this.list.addHeader(Component.translatable("chunksweeper.config.section.exit"));
		this.list.addBig(OptionInstance.createBoolean(
			"chunksweeper.config.pruneOnWorldExit",
			OptionInstance.cachedConstantTooltip(Component.translatable("chunksweeper.config.pruneOnWorldExit.tooltip")),
			config.pruneOnWorldExit,
			value -> config.pruneOnWorldExit = value
		));
		this.list.addBig(new OptionInstance<>(
			"chunksweeper.config.exitSummarySeconds",
			OptionInstance.cachedConstantTooltip(Component.translatable("chunksweeper.config.exitSummarySeconds.tooltip")),
			(caption, seconds) -> Options.genericValueLabel(caption, seconds == 0 ? CommonComponents.OPTION_OFF : Component.translatable("chunksweeper.config.seconds", seconds)),
			new OptionInstance.IntRange(0, ChunkSweeperConfig.MAX_EXIT_SUMMARY_SECONDS),
			Math.clamp(config.exitSummarySeconds, 0, ChunkSweeperConfig.MAX_EXIT_SUMMARY_SECONDS),
			value -> config.exitSummarySeconds = value
		));

		this.list.addHeader(Component.translatable("chunksweeper.config.section.backup"));
		this.list.addBig(OptionInstance.createBoolean(
			"chunksweeper.config.overwriteBackups",
			OptionInstance.cachedConstantTooltip(Component.translatable("chunksweeper.config.overwriteBackups.tooltip")),
			config.overwriteBackups,
			value -> config.overwriteBackups = value
		));
	}

	@Override
	public void removed() {
		ChunkSweeperConfig.save();
	}
}
