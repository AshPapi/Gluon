package net.ashpapi.papioptimization.mixin.memory;

import net.ashpapi.papioptimization.memory.PapiShapeCanonicalizer;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase$Cache", priority = 1500)
public abstract class BlockBehaviourCacheDeduplicationMixin {

    @Shadow @Final @Mutable public VoxelShape collisionShape;
    @Shadow @Final @Mutable private boolean[] faceSturdy;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$deduplicateCacheArrays(CallbackInfo ci) {
        this.collisionShape = PapiShapeCanonicalizer.internShape(this.collisionShape);
        this.faceSturdy = PapiShapeCanonicalizer.internFaceSturdy(this.faceSturdy);
    }
}
