package com.agustincantero.chunksweeper.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

final class Toasts {
	private static final SystemToast.SystemToastId PRUNE = new SystemToast.SystemToastId();

	private Toasts() {
	}

	static void show(Minecraft minecraft, SystemToast.SystemToastId id, Component title, Component message) {
		minecraft.gui.toastManager().addToast(new SystemToast(id, title, message));
	}

	static void showPrune(Minecraft minecraft, Component title, Component message) {
		show(minecraft, PRUNE, title, message);
	}

	static long toMegabytes(long bytes) {
		return (bytes + 1048575) / 1048576;
	}

	static String formatMegabytes(Minecraft minecraft, long bytes) {
		// Uses the game language (en_us -> en-US) instead of the system locale for the decimal separator.
		Locale locale = Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_', '-'));
		return String.format(locale, "%.1f", bytes / 1048576.0);
	}
}
