package net.ashpapi.papioptimization.mixin.sculk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.ashpapi.papioptimization.sculk.IChunkListenerAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LevelChunk.class, priority = 1500)
public abstract class LevelChunkListenerMixin implements IChunkListenerAccess {
    @Shadow @Final
    private Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;

    @Override
    public @Nullable GameEventListenerRegistry papi$getListenerRegistryIfPresent(int sectionY) {
        return this.gameEventListenerRegistrySections.get(sectionY);
    }

    @Override
    public boolean papi$hasAnyListenerRegistries() {
        return !this.gameEventListenerRegistrySections.isEmpty();
    }
}
