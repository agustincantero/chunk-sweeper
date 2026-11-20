package com.agustincantero.chunksweeper.client.mixin;

import com.agustincantero.chunksweeper.client.ExitPruner;
import net.minecraft.CrashReport;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@Shadow
	@Final
	protected LevelStorageSource.LevelStorageAccess storageSource;

	@Unique
	private volatile boolean chunksweeper$crashed;

	@Inject(method = "onServerCrash", at = @At("HEAD"))
	private void chunksweeper$markCrashed(CrashReport report, CallbackInfo ci) {
		this.chunksweeper$crashed = true;
	}

	// At the end of stopServer every level is saved and closed and the session lock is released.
	@Inject(method = "stopServer", at = @At("TAIL"))
	private void chunksweeper$pruneAfterStop(CallbackInfo ci) {
		if ((Object) this instanceof IntegratedServer && !this.chunksweeper$crashed) {
			ExitPruner.prune(this.storageSource.getLevelId());
		}
	}
}
