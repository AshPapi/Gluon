package net.ashpapi.papioptimization.mixin.chunk;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.chunk.PalettedContainer$Data", priority = 1500)
public interface PalettedContainerDataAccessor<T> {

    @Accessor("storage")
    BitStorage papi$getStorage();

    @Accessor("palette")
    Palette<T> papi$getPalette();
}
