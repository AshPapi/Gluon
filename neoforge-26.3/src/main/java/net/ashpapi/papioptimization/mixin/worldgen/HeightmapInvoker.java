package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Heightmap.class, priority = 1500)
public interface HeightmapInvoker {
    @Invoker("setHeight")
    void papi$invokeSetHeight(int x, int z, int height);
}
