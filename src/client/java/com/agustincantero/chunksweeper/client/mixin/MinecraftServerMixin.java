package com.agustincantero.chunksweeper.client.mixin;

import com.agustincantero.chunksweeper.client.ExitPruner;
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

	// IntegratedServer overrides onServerCrash without calling super, so the call site in runServer is hooked instead.
	@Inject(
		method = "runServer",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;onServerCrash(Lnet/minecraft/CrashReport;)V")
	)
	private void chunksweeper$markCrashed(CallbackInfo ci) {
		this.chunksweeper$crashed = true;
	}

	// Blocking halts are only used by Minecraft.emergencySave (client crash) and the JVM shutdown hook.
	@Inject(method = "halt", at = @At("HEAD"))
	private void chunksweeper$markEmergencyHalt(boolean wait, CallbackInfo ci) {
		if (wait) {
			this.chunksweeper$crashed = true;
		}
	}

	// At the end of stopServer every level is saved and closed and the session lock is released.
	@Inject(method = "stopServer", at = @At("TAIL"))
	private void chunksweeper$pruneAfterStop(CallbackInfo ci) {
		if ((Object) this instanceof IntegratedServer && !this.chunksweeper$crashed) {
			ExitPruner.prune(this.storageSource.getLevelId());
		}
	}
}
