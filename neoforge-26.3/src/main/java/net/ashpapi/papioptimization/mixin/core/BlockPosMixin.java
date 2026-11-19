package net.ashpapi.papioptimization.mixin.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPos.class, priority = 1500)
public abstract class BlockPosMixin {

    /**
     * Eliminates redundant endpoint BlockPos object allocations (2 heap objects per call)
     * when iterating blocks inside an AABB. Passes raw floor coordinates directly to betweenClosed(IIIIII).
     */
    @Inject(method = "betweenClosed(Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastBetweenClosedAabb(AABB aabb, CallbackInfoReturnable<Iterable<BlockPos>> cir) {
        int x1 = Mth.floor(aabb.minX);
        int y1 = Mth.floor(aabb.minY);
        int z1 = Mth.floor(aabb.minZ);
        int x2 = Mth.floor(aabb.maxX);
        int y2 = Mth.floor(aabb.maxY);
        int z2 = Mth.floor(aabb.maxZ);
        cir.setReturnValue(BlockPos.betweenClosed(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        ));
    }
}
