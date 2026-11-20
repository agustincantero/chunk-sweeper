package com.agustincantero.chunksweeper.client.mixin;

import com.agustincantero.chunksweeper.client.PrunedBackup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EditWorldScreen.class)
abstract class EditWorldScreenMixin {
	@Shadow
	@Final
	private static Component BACKUP_BUTTON;

	@Shadow
	@Final
	private BooleanConsumer callback;

	@Shadow
	@Final
	private LevelStorageAccess levelAccess;

	// Only the "Make Backup" button is changed, the backups made before optimizing or recovering a world stay complete.
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;"
		)
	)
	private Button.Builder chunksweeper$usePrunedBackup(Component message, Button.OnPress onPress, Operation<Button.Builder> original) {
		if (message == BACKUP_BUTTON) {
			onPress = button -> PrunedBackup.makeBackupAndShowToast(this.levelAccess)
				.thenAcceptAsync(success -> this.callback.accept(!success), Minecraft.getInstance());
		}
		return original.call(message, onPress);
	}
}
