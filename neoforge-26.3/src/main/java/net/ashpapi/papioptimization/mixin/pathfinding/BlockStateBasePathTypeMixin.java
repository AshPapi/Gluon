package net.ashpapi.papioptimization.mixin.pathfinding;

import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.ashpapi.papioptimization.pathfinding.PapiBlockStatePathType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500)
public abstract class BlockStateBasePathTypeMixin implements PapiBlockStatePathType {

    @Shadow
    private boolean isAir;

    @Shadow
    public abstract net.minecraft.world.level.block.Block getBlock();

    @Shadow
    public abstract FluidState getFluidState();

    @Unique
    private PathType papi$cachedPathType;

    @Unique
    private volatile int papi$cachedPathTypeEpoch = 0;

    @Unique
    private volatile boolean papi$cacheableState = false;

    @Inject(method = "initCache", at = @At("RETURN"))
    private void papi$resetPathTypeOnInitCache(CallbackInfo ci) {
        FluidState fluid = this.getFluidState();
        boolean vanillaFluid = fluid.isEmpty() || fluid.getType().getClass().getName().startsWith("net.minecraft.");
        this.papi$cacheableState = vanillaFluid && PapiTagEpoch.isCacheableBlock(this.getBlock());
        this.papi$cachedPathType = this.isAir ? PathType.OPEN : null;
        this.papi$cachedPathTypeEpoch = 0;
    }

    @Override
    public boolean papi$isCacheable() {
        return this.papi$cacheableState;
    }

    @Override
    public PathType papi$getCachedPathType(int epoch) {
        if (!this.papi$cacheableState) {
            return null;
        }
        if (this.isAir) {
            return PathType.OPEN;
        }
        if (this.papi$cachedPathTypeEpoch != epoch) {
            return null;
        }
        return this.papi$cachedPathType;
    }

    @Override
    public void papi$setCachedPathType(PathType type, int epoch) {
        if (this.papi$cacheableState && !this.isAir) {
            this.papi$cachedPathType = type;
            this.papi$cachedPathTypeEpoch = epoch;
        }
    }
}
