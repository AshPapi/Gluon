package net.ashpapi.papioptimization.mixin.block;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LeavesBlock.class, priority = 1500)
public abstract class LeavesDecayOptimizationMixin {

    @Unique
    private static final OptionalInt[] PAPI$DIST_CACHE = new OptionalInt[8];

    static {
        for (int i = 0; i <= 7; i++) {
            PAPI$DIST_CACHE[i] = OptionalInt.of(i);
        }
    }

    @Inject(
        method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/ScheduledTickAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$skipPersistentLeavesTicks(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess tickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random,
        CallbackInfoReturnable<BlockState> cir
    ) {
        if (state.getValue(LeavesBlock.PERSISTENT)) {
            if (state.getValue(LeavesBlock.WATERLOGGED)) {
                tickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }
            cir.setReturnValue(state);
        }
    }

    @Inject(
        method = "getOptionalDistanceAt(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/OptionalInt;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void papi$fastGetDistanceAt(BlockState state, CallbackInfoReturnable<OptionalInt> cir) {
        if (state.is(BlockTags.PREVENTS_NEARBY_LEAF_DECAY)) {
            cir.setReturnValue(PAPI$DIST_CACHE[0]);
            return;
        }

        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            int dist = state.getValue(LeavesBlock.DISTANCE);
            cir.setReturnValue(dist >= 0 && dist <= 7 ? PAPI$DIST_CACHE[dist] : OptionalInt.of(dist));
            return;
        }

        cir.setReturnValue(OptionalInt.empty());
    }
}
