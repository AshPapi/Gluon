package net.ashpapi.papioptimization.mixin.blockentity;

import net.ashpapi.papioptimization.core.FastBeaconHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PapiOptimization Fast Beacon Mixin:
 * - Replaces 1,176 chunk section scan with O(P) direct player list search.
 * - Replaces 164 BlockPos allocations in updateBase with zero-allocation chunk caching.
 * - 100% vanilla parity.
 */
@Mixin(value = BeaconBlockEntity.class, priority = 1500)
public abstract class FastBeaconMixin {

    @Inject(method = "updateBase", at = @At("HEAD"), cancellable = true)
    private static void papi$fastUpdateBase(Level level, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FastBeaconHandler.updateBaseFast(level, x, y, z));
    }

    @Inject(method = "applyEffects", at = @At("HEAD"), cancellable = true)
    private static void papi$fastApplyEffects(
            Level level, BlockPos worldPosition, int levels,
            Holder<MobEffect> primaryPower, Holder<MobEffect> secondaryPower,
            CallbackInfo ci) {
        ci.cancel();
        FastBeaconHandler.applyEffectsFast(level, worldPosition, levels, primaryPower, secondaryPower);
    }
}
