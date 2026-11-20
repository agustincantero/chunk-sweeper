package com.agustincantero.chunksweeper.client.mixin;

import com.agustincantero.chunksweeper.client.SavingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@ModifyArg(
		method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	private Screen chunksweeper$showPruneProgress(Screen screen) {
		return new SavingScreen();
	}
}
