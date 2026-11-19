package net.ashpapi.papioptimization.mixin.weather;

import java.util.Optional;
import net.ashpapi.papioptimization.core.FastCopperOxidizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChangeOverTimeBlock.class, priority = 1500)
public interface ChangeOverTimeBlockMixin<T extends Enum<T>> {

    @SuppressWarnings("unchecked")
    @Inject(method = "getNextState", at = @At("HEAD"), cancellable = true)
    default void papi$fastCopperOxidation(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfoReturnable<Optional<BlockState>> cir) {
        cir.setReturnValue(FastCopperOxidizer.getNextStateFast((ChangeOverTimeBlock<T>) this, state, level, pos, random));
    }
}
