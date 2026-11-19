package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FlowingFluid.class, priority = 1500)
public abstract class FlowingFluidMixin {

    @Inject(method = "canPassThroughWall", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCanPassThroughWall(
            Direction direction, BlockGetter level,
            BlockPos fromPos, BlockState fromState,
            BlockPos toPos, BlockState toState,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!SharedConstants.DEBUG_DISABLE_LIQUID_SPREADING
                && (!SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD || toPos.getZ() >= 0)
                && papi$isEmptyFluidOrAir(fromState)
                && papi$isEmptyFluidOrAir(toState)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean papi$isEmptyFluidOrAir(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }
}
