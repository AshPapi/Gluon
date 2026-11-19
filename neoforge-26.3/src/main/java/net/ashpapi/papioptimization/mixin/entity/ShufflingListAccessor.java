package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.ai.behavior.ShufflingList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = ShufflingList.class, priority = 1500)
public interface ShufflingListAccessor<U> {
    @Accessor("entries")
    List<ShufflingList.WeightedEntry<U>> papi$getEntries();
}
