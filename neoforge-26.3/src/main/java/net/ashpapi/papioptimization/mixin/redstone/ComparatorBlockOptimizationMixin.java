package net.ashpapi.papioptimization.mixin.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PapiOptimization Fast Comparator Input Signal:
 * In auto-farms and sorting systems, comparators read directly from containers
 * (chests, hoppers, barrels, crafters, composters, cauldrons).
 *
 * In vanilla, ComparatorBlock.getInputSignal calls DiodeBlock.getInputSignal first,
 * which performs redstone power lookups, only to immediately overwrite the result
 * with directState.getAnalogOutputSignal() if hasAnalogOutputSignal() is true.
 *
 * Fast-pathing direct containers bypasses unnecessary wire signal lookups
 * while preserving 100% vanilla redstone signal strength and auto-farm parity.
 */
@Mixin(value = ComparatorBlock.class, priority = 1500)
public abstract class ComparatorBlockOptimizationMixin extends DiodeBlock {

    protected ComparatorBlockOptimizationMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Shadow
    private @Nullable ItemFrame getItemFrame(Level level, Direction direction, BlockPos pos) {
        return null;
    }

    @Inject(method = "getInputSignal", at = @At("HEAD"), cancellable = true)
    private void papi$fastDirectContainerInputSignal(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        Direction dir = state.getValue(FACING);
        BlockPos adjPos = pos.relative(dir);
        BlockState adjState = level.getBlockState(adjPos);

        if (adjState.hasAnalogOutputSignal()) {
            cir.setReturnValue(adjState.getAnalogOutputSignal(level, adjPos, dir.getOpposite()));
            return;
        }

        int signal = super.getInputSignal(level, pos, state);
        if (signal < 15 && adjState.isRedstoneConductor(level, adjPos)) {
            BlockPos farPos = adjPos.relative(dir);
            BlockState farState = level.getBlockState(farPos);
            ItemFrame itemFrame = this.getItemFrame(level, dir, farPos);
            int extra = Math.max(
                    itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
                    farState.hasAnalogOutputSignal() ? farState.getAnalogOutputSignal(level, farPos, dir.getOpposite()) : Integer.MIN_VALUE
            );
            if (extra != Integer.MIN_VALUE) {
                signal = extra;
            }
        }

        cir.setReturnValue(signal);
    }
}
