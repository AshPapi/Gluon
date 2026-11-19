package net.ashpapi.papioptimization.mixin.core;

import com.google.common.cache.LoadingCache;
import net.minecraft.util.EncoderCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EncoderCache.class, priority = 1500)
public interface EncoderCacheAccessor {

    @Accessor("cache")
    LoadingCache<?, ?> papi$getCache();
}
