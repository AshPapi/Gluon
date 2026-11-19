package net.ashpapi.papioptimization.client;

import com.google.common.cache.LoadingCache;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.mixin.core.DataComponentsAccessor;
import net.ashpapi.papioptimization.mixin.core.EncoderCacheAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EncoderCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GpuMemoryLeakHandler {

    private static final GpuMemoryLeakHandler INSTANCE = new GpuMemoryLeakHandler();

    public static GpuMemoryLeakHandler getInstance() {
        return INSTANCE;
    }

    private static void cleanupCaches() {
        MapDecorationCache.clear();
        PapiOcclusionEngine.clear();
        PapiEbeRuntime.clearCache();
        PapiQuadCanonicalizer.clear();
        PapiClientCaches.invalidateAll();
        EncoderCache encoderCache = DataComponentsAccessor.papi$getEncoderCache();
        if (encoderCache != null) {
            LoadingCache<?, ?> cache = ((EncoderCacheAccessor) encoderCache).papi$getCache();
            if (cache != null) {
                cache.invalidateAll();
            }
        }
    }

    private static void cleanupLevelCaches() {
        PapiEbeRuntime.resetFrameContext();
        PapiOcclusionEngine.clear();
        MapDecorationCache.clear();
    }

    public void onLevelChange() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(GpuMemoryLeakHandler::cleanupLevelCaches);
        } else {
            cleanupLevelCaches();
        }
    }

    public void onDisconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(GpuMemoryLeakHandler::cleanupCaches);
        } else {
            cleanupCaches();
        }
    }
}
