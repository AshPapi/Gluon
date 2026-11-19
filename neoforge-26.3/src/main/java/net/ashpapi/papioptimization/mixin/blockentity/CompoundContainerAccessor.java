package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CompoundContainer.class, priority = 1500)
public interface CompoundContainerAccessor {
    @Accessor("container1")
    Container papi$getContainer1();

    @Accessor("container2")
    Container papi$getContainer2();
}
