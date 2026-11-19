package net.ashpapi.papioptimization.mixin.client;

import java.util.concurrent.atomic.AtomicReference;
import net.ashpapi.papioptimization.client.MapDecorationCache;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.client.PapiQuadCanonicalizer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelManager.class, priority = 1500)
public abstract class ModelManagerMemoryMixin {

    @Shadow @Final private AtomicReference<ModelBakery> modelBakery;

    @Inject(method = "apply", at = @At("RETURN"))
    private void papi$releaseTemporaryModelBakery(CallbackInfo ci) {
        this.modelBakery.set(null);
        PapiQuadCanonicalizer.clear();
        MapDecorationCache.clear();
        PapiClientCaches.invalidateAll();
    }
}
