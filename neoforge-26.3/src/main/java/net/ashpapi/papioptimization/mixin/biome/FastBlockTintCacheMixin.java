package net.ashpapi.papioptimization.mixin.biome;

import net.minecraft.client.color.block.BlockTintCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = BlockTintCache.class, priority = 1500)
public abstract class FastBlockTintCacheMixin {

    @ModifyConstant(method = "findOrCreateChunkCache", constant = @Constant(intValue = 256))
    private int papi$expandTintCacheCapacity(int original) {
        return 1024;
    }
}
