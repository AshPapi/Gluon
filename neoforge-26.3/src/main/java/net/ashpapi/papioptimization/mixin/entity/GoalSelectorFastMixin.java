package net.ashpapi.papioptimization.mixin.entity;

import net.ashpapi.papioptimization.access.WrappedGoalFlagMask;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;
import java.util.Map;

@Mixin(value = GoalSelector.class, priority = 1500)
public abstract class GoalSelectorFastMixin {

    @Shadow @Final private static WrappedGoal NO_GOAL;

    @Unique
    private static final Goal.Flag[] PAPI$FLAG_VALUES = Goal.Flag.values();

    @Inject(method = "goalCanBeReplacedForAllFlags", at = @At("HEAD"), cancellable = true)
    private static void papi$fastGoalCanBeReplaced(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> lockedFlags, CallbackInfoReturnable<Boolean> cir) {
        if (PAPI$FLAG_VALUES.length > 64) {
            return;
        }
        long mask = ((WrappedGoalFlagMask) goal).papi$getFlagMask();
        if (mask == 0L) {
            cir.setReturnValue(true);
            return;
        }
        while (mask != 0L) {
            int ordinal = Long.numberOfTrailingZeros(mask);
            mask &= mask - 1L;
            if (!lockedFlags.getOrDefault(PAPI$FLAG_VALUES[ordinal], NO_GOAL).canBeReplacedBy(goal)) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "goalContainsAnyFlags", at = @At("HEAD"), cancellable = true)
    private static void papi$fastGoalContainsAnyFlags(WrappedGoal goal, EnumSet<Goal.Flag> disabledFlags, CallbackInfoReturnable<Boolean> cir) {
        if (disabledFlags.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        if (PAPI$FLAG_VALUES.length > 64) {
            return;
        }
        long mask = ((WrappedGoalFlagMask) goal).papi$getFlagMask();
        if (mask == 0L) {
            cir.setReturnValue(false);
            return;
        }
        while (mask != 0L) {
            int ordinal = Long.numberOfTrailingZeros(mask);
            mask &= mask - 1L;
            if (disabledFlags.contains(PAPI$FLAG_VALUES[ordinal])) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
