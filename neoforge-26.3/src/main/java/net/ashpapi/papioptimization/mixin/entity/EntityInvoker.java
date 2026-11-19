package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Entity.class, priority = 1500)
public interface EntityInvoker {

    @Invoker("applyEffectsFromBlocks")
    void papi$applyEffectsFromBlocks();
}
