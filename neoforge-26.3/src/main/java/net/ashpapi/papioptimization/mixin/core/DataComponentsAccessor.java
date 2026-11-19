package net.ashpapi.papioptimization.mixin.core;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.EncoderCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DataComponents.class, priority = 1500)
public interface DataComponentsAccessor {

    @Accessor("ENCODER_CACHE")
    static EncoderCache papi$getEncoderCache() {
        throw new AssertionError();
    }
}
