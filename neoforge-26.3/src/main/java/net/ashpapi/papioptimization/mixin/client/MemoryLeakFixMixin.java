package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.GpuMemoryLeakHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1500)
public abstract class MemoryLeakFixMixin {

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void papi$onDisconnectCleanup(Screen screen, boolean transfer, boolean isDisconnectingFromWorld, CallbackInfo ci) {
        GpuMemoryLeakHandler.getInstance().onDisconnect();
    }

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("HEAD"))
    private void papi$onLevelChangeCleanup(ClientLevel level, CallbackInfo ci) {
        GpuMemoryLeakHandler.getInstance().onLevelChange();
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void papi$onClearClientLevelCleanup(Screen screen, CallbackInfo ci) {
        GpuMemoryLeakHandler.getInstance().onDisconnect();
    }
}
