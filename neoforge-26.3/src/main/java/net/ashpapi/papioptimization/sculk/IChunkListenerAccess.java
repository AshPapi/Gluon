package net.ashpapi.papioptimization.sculk;

import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.jspecify.annotations.Nullable;

public interface IChunkListenerAccess {
    @Nullable
    GameEventListenerRegistry papi$getListenerRegistryIfPresent(int sectionY);
    boolean papi$hasAnyListenerRegistries();
}
