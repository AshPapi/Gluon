package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.BooleanSupplier;
import net.ashpapi.papioptimization.server.PapiAutosaveState;
import net.ashpapi.papioptimization.server.PapiMsptController;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1500)
public abstract class MinecraftServerMsptMixin {

    @Unique
    private long papi$tickStartNanos;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void papi$markServerTickStart(BooleanSupplier haveTime, CallbackInfo ci) {
        this.papi$tickStartNanos = System.nanoTime();
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void papi$recordServerTickDuration(BooleanSupplier haveTime, CallbackInfo ci) {
        long start = this.papi$tickStartNanos;
        if (start != 0L) {
            PapiMsptController.recordTickNanos(System.nanoTime() - start);
        }
    }

    @WrapMethod(method = "autoSave")
    private void papi$markAutoSaveContext(Operation<Void> original) {
        boolean prev = PapiAutosaveState.isInAutoSave();
        PapiAutosaveState.setInAutoSave(true);
        try {
            original.call();
        } finally {
            PapiAutosaveState.setInAutoSave(prev);
        }
    }
}
