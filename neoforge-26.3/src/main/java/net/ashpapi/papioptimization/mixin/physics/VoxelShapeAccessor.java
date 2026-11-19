package net.ashpapi.papioptimization.mixin.physics;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VoxelShape.class, priority = 1500)
public interface VoxelShapeAccessor {
    @Accessor("shape")
    DiscreteVoxelShape papi$getDiscreteShape();
}
