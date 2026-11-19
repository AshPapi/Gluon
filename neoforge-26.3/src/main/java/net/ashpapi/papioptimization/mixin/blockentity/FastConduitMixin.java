package net.ashpapi.papioptimization.mixin.blockentity;

import java.util.List;
import net.ashpapi.papioptimization.core.FastConduitHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PapiOptimization Fast Conduit Mixin:
 * - Precomputed 42 prismarine frame offsets: bypasses 125 3D-loop iterations and allocations.
 * - Replaces 4,056 chunk section scan with O(P) direct player list search.
 * - 100% vanilla parity.
 */
@Mixin(value = ConduitBlockEntity.class, priority = 1500)
public abstract class FastConduitMixin {

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private static void papi$fastUpdateShape(
            Level level, BlockPos worldPosition, List<BlockPos> effectBlocks,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(FastConduitHandler.updateShapeFast(level, worldPosition, effectBlocks));
    }

    @Inject(method = "applyEffects", at = @At("HEAD"), cancellable = true)
    private static void papi$fastApplyEffects(
            Level level, BlockPos worldPosition, List<BlockPos> effectBlocks,
            CallbackInfo ci) {
        ci.cancel();
        FastConduitHandler.applyEffectsFast(level, worldPosition, effectBlocks);
    }
}
