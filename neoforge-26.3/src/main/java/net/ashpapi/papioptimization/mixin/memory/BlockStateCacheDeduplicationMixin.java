package net.ashpapi.papioptimization.mixin.memory;

import net.ashpapi.papioptimization.memory.PapiShapeCanonicalizer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500)
public abstract class BlockStateCacheDeduplicationMixin {

    @Shadow private VoxelShape occlusionShape;
    @Shadow private VoxelShape[] occlusionShapesByFace;

    @Inject(method = "initCache", at = @At("RETURN"))
    private void papi$canonicalizeOcclusionShapes(CallbackInfo ci) {
        this.occlusionShape = PapiShapeCanonicalizer.internShape(this.occlusionShape);
        this.occlusionShapesByFace = PapiShapeCanonicalizer.internFaceArray(this.occlusionShapesByFace);
    }
}
