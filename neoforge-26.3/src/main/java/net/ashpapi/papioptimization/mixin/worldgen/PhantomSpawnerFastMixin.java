package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PhantomSpawner.class, priority = 1500)
public abstract class PhantomSpawnerFastMixin {

    @Shadow private int nextTick;

    @WrapMethod(method = "tick")
    private void papi$fastPhantomTick(ServerLevel level, boolean spawnEnemies, Operation<Void> original) {
        if (!spawnEnemies || !level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
            return;
        }
        --this.nextTick;
        if (this.nextTick > 0) {
            return;
        }
        if (level.getSkyDarken() < 5 && level.dimensionType().hasSkyLight()) {
            this.nextTick += (60 + level.getRandom().nextInt(60)) * 20;
            return;
        }
        ++this.nextTick;
        original.call(level, spawnEnemies);
    }
}
