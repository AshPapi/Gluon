package net.ashpapi.papioptimization.mixin.entity;

import net.ashpapi.papioptimization.access.WrappedGoalFlagMask;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

@Mixin(value = WrappedGoal.class, priority = 1500)
public abstract class WrappedGoalMixin implements WrappedGoalFlagMask {

    @Shadow @Final private Goal goal;

    @Override
    public long papi$getFlagMask() {
        return ((WrappedGoalFlagMask) this.goal).papi$getFlagMask();
    }
}
