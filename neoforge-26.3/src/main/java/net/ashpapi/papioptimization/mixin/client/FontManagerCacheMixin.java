package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = FontManager.class, priority = 1500)
public abstract class FontManagerCacheMixin {

    @Inject(method = "apply", at = @At("TAIL"))
    private void papi$invalidateClientCachesOnFontReload(@Coerce Object preparation, ProfilerFiller profiler, CallbackInfo ci) {
        PapiClientCaches.invalidateAll();
    }

    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void papi$invalidateClientCachesOnFontOptions(Options options, CallbackInfo ci) {
        PapiClientCaches.invalidateAll();
    }
}
