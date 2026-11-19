package net.ashpapi.papioptimization.mixin.poi;

import java.util.Optional;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SectionStorage.class, priority = 1500)
public interface SectionStorageAccessor<R> {

    @Invoker("getOrLoad")
    Optional<R> papi$getOrLoad(long key);

    @Accessor("levelHeightAccessor")
    LevelHeightAccessor papi$getLevelHeightAccessor();
}
