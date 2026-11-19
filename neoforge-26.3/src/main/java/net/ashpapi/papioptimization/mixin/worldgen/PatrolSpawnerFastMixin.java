package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Patrol Spawner Fast Skip:
 * Bypasses pillager patrol spawn scanning when enemy spawning is disabled
 * or in dimensions without skylight (Nether, End), preserving 100% vanilla parity.
 */
@Mixin(value = PatrolSpawner.class, priority = 1500)
public abstract class PatrolSpawnerFastMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$fastSkipPatrolTick(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
        if (!spawnEnemies || !level.dimensionType().hasSkyLight()) {
            ci.cancel();
        }
    }
}
