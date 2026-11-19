package net.ashpapi.papioptimization.mixin.pathfinding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Arrays;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.ashpapi.papioptimization.pathfinding.PapiBlockStatePathType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WalkNodeEvaluator.class, priority = 1500)
public abstract class WalkNodeEvaluatorFastMixin extends NodeEvaluator {

    @Shadow
    @Final
    private Long2ObjectMap<PathType> pathTypesByPosCacheByMob;

    @Shadow
    public abstract PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob);

    @Unique
    private static final int PAPI$CACHE_SIZE = 512;

    @Unique
    private static final int PAPI$CACHE_MASK = PAPI$CACHE_SIZE - 1;

    @Unique
    private final long[] papi$posKeys = new long[PAPI$CACHE_SIZE];

    @Unique
    private final PathType[] papi$posVals = new PathType[PAPI$CACHE_SIZE];

    @Unique
    private boolean papi$posCacheDirty = true;

    @Unique
    private void papi$clearPosCache() {
        if (this.papi$posCacheDirty) {
            Arrays.fill(this.papi$posKeys, Long.MIN_VALUE);
            Arrays.fill(this.papi$posVals, null);
            this.papi$posCacheDirty = false;
        }
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void papi$resetCacheOnPrepare(PathNavigationRegion region, Mob mob, CallbackInfo ci) {
        papi$clearPosCache();
    }

    @Inject(method = "done", at = @At("HEAD"))
    private void papi$resetCacheOnDone(CallbackInfo ci) {
        papi$clearPosCache();
    }

    @WrapMethod(method = "getCachedPathType(III)Lnet/minecraft/world/level/pathfinder/PathType;")
    private PathType papi$fastCachedPathType(int x, int y, int z, Operation<PathType> original) {
        long key = BlockPos.asLong(x, y, z);
        if (key == Long.MIN_VALUE) {
            PathType mapVal = this.pathTypesByPosCacheByMob.get(key);
            if (mapVal != null) {
                return mapVal;
            }
            PathType computed = this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob);
            this.pathTypesByPosCacheByMob.put(key, computed);
            return computed;
        }
        int slot = (int) (key ^ (key >>> 32) ^ (key >>> 16)) & PAPI$CACHE_MASK;
        if (this.papi$posKeys[slot] == key) {
            PathType cached = this.papi$posVals[slot];
            if (cached != null) {
                return cached;
            }
        }
        PathType val = this.pathTypesByPosCacheByMob.get(key);
        if (val == null) {
            val = this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob);
            this.pathTypesByPosCacheByMob.put(key, val);
        }
        this.papi$posKeys[slot] = key;
        this.papi$posVals[slot] = val;
        this.papi$posCacheDirty = true;
        return val;
    }

    @WrapMethod(method = "getPathTypeFromState(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;")
    private static PathType papi$fastGetPathTypeFromState(BlockGetter level, BlockPos pos, Operation<PathType> original) {
        BlockState state = level.getBlockState(pos);
        PapiBlockStatePathType access = (PapiBlockStatePathType) state;
        if (!access.papi$isCacheable()) {
            return original.call(level, pos);
        }
        if (state.isAir()) {
            return PathType.OPEN;
        }
        int epoch = PapiTagEpoch.current();
        PathType cached = access.papi$getCachedPathType(epoch);
        if (cached != null) {
            return cached;
        }
        PathType computed = original.call(level, pos);
        if (computed != null && PapiTagEpoch.current() == epoch) {
            access.papi$setCachedPathType(computed, epoch);
        }
        return computed;
    }
}
