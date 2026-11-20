package com.agustincantero.chunksweeper.client;

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
}
