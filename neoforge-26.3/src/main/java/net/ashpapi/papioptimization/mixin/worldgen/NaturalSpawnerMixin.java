package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = NaturalSpawner.class, priority = 1500)
public abstract class NaturalSpawnerMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void papi$fastChunkSpawnCheck(
            ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            List<MobCategory> categories, CallbackInfo ci
    ) {
        if (categories == null || categories.isEmpty() || chunk.isEmpty()) {
            ci.cancel();
        }
    }
}
