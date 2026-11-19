package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Brain.class, priority = 1500)
public interface BrainInvoker {
    @Invoker("forgetOutdatedMemories")
    void papi$forgetOutdatedMemories();
}
