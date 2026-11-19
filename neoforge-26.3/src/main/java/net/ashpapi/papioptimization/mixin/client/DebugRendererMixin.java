package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * PapiOptimization Debug Renderer Optimizer:
 * Fast-fails DebugRenderer.emitGizmos() when no debug entries are enabled,
 * eliminating network packet listener debug allocations and renderer list iteration
 * during normal gameplay (100% of the time unless F3 debug overlays are active).
 */
@Mixin(value = DebugRenderer.class, priority = 1500)
public abstract class DebugRendererMixin {

    @Shadow @Final private List<DebugRenderer.SimpleDebugRenderer> renderers;
    @Shadow private long lastDebugEntriesVersion;

    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void papi$fastGizmosExit(Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.debugEntries == null) {
            return;
        }

        long currentVersion = mc.debugEntries.getCurrentlyEnabledVersion();
        if (this.renderers.isEmpty() && currentVersion == this.lastDebugEntriesVersion) {
            ci.cancel();
        }
    }
}
