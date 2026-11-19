package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Camera.class, priority = 1500)
public abstract class CameraMixin {

    @Unique
    private FogType papi$cachedFluidInCamera;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void papi$onSetLevel(ClientLevel level, CallbackInfo ci) {
        this.papi$cachedFluidInCamera = null;
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void papi$onUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        this.papi$cachedFluidInCamera = null;
    }

    @Inject(method = "setPosition(DDD)V", at = @At("HEAD"))
    private void papi$onSetPositionCoords(double x, double y, double z, CallbackInfo ci) {
        this.papi$cachedFluidInCamera = null;
    }

    @Inject(method = "setPosition(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void papi$onSetPosition(Vec3 pos, CallbackInfo ci) {
        this.papi$cachedFluidInCamera = null;
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void papi$onReset(CallbackInfo ci) {
        this.papi$cachedFluidInCamera = null;
    }

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void papi$getCachedFluidInCamera(CallbackInfoReturnable<FogType> cir) {
        FogType cached = this.papi$cachedFluidInCamera;
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getFluidInCamera", at = @At("RETURN"))
    private void papi$storeFluidInCamera(CallbackInfoReturnable<FogType> cir) {
        this.papi$cachedFluidInCamera = cir.getReturnValue();
    }
}
