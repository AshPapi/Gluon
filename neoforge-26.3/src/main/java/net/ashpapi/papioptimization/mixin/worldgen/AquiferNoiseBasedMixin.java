package net.ashpapi.papioptimization.mixin.worldgen;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Aquifer.NoiseBasedAquifer.class, priority = 1500)
public abstract class AquiferNoiseBasedMixin {

    @Shadow @Final private int minGridX;
    @Shadow @Final private int minGridY;
    @Shadow @Final private int minGridZ;
    @Shadow @Final private int gridSizeX;
    @Shadow @Final private int gridSizeZ;
    @Shadow @Final private DensitySampler.Bound surfaceLevel;
    @Shadow @Final private Long2IntMap surfaceLevelCache;

    @Unique
    private long papi$lastSurfaceKey = Long.MIN_VALUE;

    @Unique
    private int papi$lastSurfaceVal;

    @Unique
    private boolean papi$hasLastSurface;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initSurfaceCacheSentinel(CallbackInfo ci) {
        this.surfaceLevelCache.defaultReturnValue(Integer.MIN_VALUE);
    }

    @Inject(method = "getIndex", at = @At("HEAD"), cancellable = true)
    private void papi$inlineGetIndex(int gridX, int gridY, int gridZ, CallbackInfoReturnable<Integer> cir) {
        int x = gridX - this.minGridX;
        int y = gridY - this.minGridY;
        int z = gridZ - this.minGridZ;
        cir.setReturnValue((y * this.gridSizeZ + z) * this.gridSizeX + x);
    }

    @Inject(method = "surfaceLevel(II)I", at = @At("HEAD"), cancellable = true)
    private void papi$fastSurfaceLevel(int x, int z, CallbackInfoReturnable<Integer> cir) {
        int qx = QuartPos.toBlock(QuartPos.fromBlock(x));
        int qz = QuartPos.toBlock(QuartPos.fromBlock(z));
        long key = ChunkPos.pack(qx, qz);
        if (this.papi$hasLastSurface && key == this.papi$lastSurfaceKey) {
            cir.setReturnValue(this.papi$lastSurfaceVal);
            return;
        }
        int cached = this.surfaceLevelCache.get(key);
        if (cached != Integer.MIN_VALUE) {
            this.papi$lastSurfaceKey = key;
            this.papi$lastSurfaceVal = cached;
            this.papi$hasLastSurface = true;
            cir.setReturnValue(cached);
            return;
        }
        int computed = Mth.floor(this.surfaceLevel.sampleValue(qx, 0, qz));
        if (computed != Integer.MIN_VALUE) {
            this.surfaceLevelCache.put(key, computed);
            this.papi$lastSurfaceKey = key;
            this.papi$lastSurfaceVal = computed;
            this.papi$hasLastSurface = true;
        }
        cir.setReturnValue(computed);
    }
}
