package com.agustincantero.chunksweeper.client;

import com.agustincantero.chunksweeper.prune.PruneProgress;
import com.agustincantero.chunksweeper.prune.PruneResult;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;

/**
 * The vanilla "Saving world" screen, plus the progress and the summary of the exit prune.
 */
public final class SavingScreen extends GenericMessageScreen {
	private static final int BAR_WIDTH = 200;
	private static final int BAR_HEIGHT = 6;
	private static final int LINE_HEIGHT = 12;
	private static final int BAR_BORDER = 0xFFFFFFFF;
	private static final int BAR_FILL = 0xFF55FF55;

	public SavingScreen() {
		super(Gui.SAVING_LEVEL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int centerX = this.width / 2;
		int y = this.height / 2 + 32;

		PruneProgress progress = ExitPruner.running();
		if (progress != null) {
			graphics.centeredText(this.font, Component.translatable("chunksweeper.prune.screen.running").withStyle(ChatFormatting.GRAY), centerX, y, -1);
			y += LINE_HEIGHT + 2;
			this.drawBar(graphics, centerX - BAR_WIDTH / 2, y, progress.fraction());
			y += BAR_HEIGHT + 6;
			Component details = Component.translatable("chunksweeper.prune.screen.progress", progress.done(), progress.total(), progress.removedChunks());
			graphics.centeredText(this.font, details.copy().withStyle(ChatFormatting.GRAY), centerX, y, -1);
			return;
		}

		PruneResult summary = ExitPruner.summary();
		if (summary != null) {
			graphics.centeredText(this.font, Component.translatable("chunksweeper.prune.screen.done", summary.removedChunks()).withStyle(ChatFormatting.GREEN), centerX, y, -1);
			y += LINE_HEIGHT;
			// Uses the game language (en_us -> en-US) instead of the system locale for the decimal separator.
			Locale locale = Locale.forLanguageTag(this.minecraft.getLanguageManager().getSelected().replace('_', '-'));
			String megabytes = String.format(locale, "%.1f", summary.bytesFreed() / 1048576.0);
			graphics.centeredText(this.font, Component.translatable("chunksweeper.prune.screen.freed", megabytes).withStyle(ChatFormatting.GRAY), centerX, y, -1);
		}
	}

	private void drawBar(GuiGraphicsExtractor graphics, int x, int y, float fraction) {
		graphics.outline(x, y, BAR_WIDTH, BAR_HEIGHT, BAR_BORDER);
		int filled = Math.round((BAR_WIDTH - 4) * fraction);
		if (filled > 0) {
			graphics.fill(x + 2, y + 2, x + 2 + filled, y + BAR_HEIGHT - 2, BAR_FILL);
		}
	}
}
