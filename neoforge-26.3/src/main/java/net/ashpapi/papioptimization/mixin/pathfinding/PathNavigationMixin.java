package net.ashpapi.papioptimization.mixin.pathfinding;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathNavigation.class, priority = 1500)
public abstract class PathNavigationMixin {

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void papi$fastExitEmptyTargets(Set<BlockPos> targets, int reachRange, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<@Nullable Path> cir) {
        if (targets.isEmpty()) {
            cir.setReturnValue(null);
        }
    }
}
