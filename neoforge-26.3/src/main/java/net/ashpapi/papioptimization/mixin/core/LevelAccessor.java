package net.ashpapi.papioptimization.mixin.core;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Level.class, priority = 1500)
public interface LevelAccessor {
    @Accessor("randValue")
    int papi$getRandValue();

    @Accessor("randValue")
    void papi$setRandValue(int value);
}
