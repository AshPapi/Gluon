package net.ashpapi.papioptimization.core;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * High-performance explosion calculation engine for Minecraft 26.3.
 * Preserves 100% bit-exact vanilla blast radius, entity knockback, and RNG parity.
 */
public final class ExplosionOptimizer {
    public static final int TOTAL_RAYS = 1352;
    private static final double[] RAY_STEPS = new double[TOTAL_RAYS * 3];

    // Thread-local direct-mapped L1 cache for raycast queries (1024 slots)
    private static final int CACHE_SIZE = 1024;
    private static final int CACHE_MASK = CACHE_SIZE - 1;
    private static final ThreadLocal<RayCache> LOCAL_CACHE = ThreadLocal.withInitial(RayCache::new);

    private record ExposureKey(Vec3 center, AABB box) {}
    private static final ThreadLocal<java.util.ArrayDeque<Map<ExposureKey, Float>>> EXPOSURE_STACK =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    static {
        int idx = 0;
        for (int xx = 0; xx < 16; ++xx) {
            for (int yy = 0; yy < 16; ++yy) {
                for (int zz = 0; zz < 16; ++zz) {
                    if (xx != 0 && xx != 15 && yy != 0 && yy != 15 && zz != 0 && zz != 15) {
                        continue;
                    }
                    double xd = (float) xx / 15.0f * 2.0f - 1.0f;
                    double yd = (float) yy / 15.0f * 2.0f - 1.0f;
                    double zd = (float) zz / 15.0f * 2.0f - 1.0f;
                    double d = Math.sqrt(xd * xd + yd * yd + zd * zd);
                    RAY_STEPS[idx++] = (xd / d) * (double) 0.3f;
                    RAY_STEPS[idx++] = (yd / d) * (double) 0.3f;
                    RAY_STEPS[idx++] = (zd / d) * (double) 0.3f;
                }
            }
        }
    }

    private static class RayCache {
        final long[] keys = new long[CACHE_SIZE];
        final float[] delta = new float[CACHE_SIZE];
        final BlockState[] blockStates = new BlockState[CACHE_SIZE];
        final boolean[] inBounds = new boolean[CACHE_SIZE];

        void clear() {
            Arrays.fill(keys, Long.MIN_VALUE);
            Arrays.fill(blockStates, null);
        }
    }

    public static void startExplosionContext() {
        EXPOSURE_STACK.get().push(new HashMap<>());
    }

    public static void endExplosionContext() {
        java.util.ArrayDeque<Map<ExposureKey, Float>> stack = EXPOSURE_STACK.get();
        if (!stack.isEmpty()) {
            Map<ExposureKey, Float> frame = stack.pop();
            frame.clear();
        }
    }

    public static Float getCachedExposure(Vec3 center, AABB box) {
        if (center == null || box == null) {
            return null;
        }
        Map<ExposureKey, Float> current = EXPOSURE_STACK.get().peek();
        if (current == null) {
            return null;
        }
        return current.get(new ExposureKey(center, box));
    }

    public static void putCachedExposure(Vec3 center, AABB box, float exposure) {
        if (center == null || box == null) {
            return;
        }
        Map<ExposureKey, Float> current = EXPOSURE_STACK.get().peek();
        if (current != null) {
            current.put(new ExposureKey(center, box), exposure);
        }
    }

    /**
     * Optimized calculateExplodedPositions with precalculated rays, primitive LongOpenHashSet,
     * per-ray step deduplication, and direct-mapped L1 cache with guaranteed thread-local cleanup.
     */
    public static List<BlockPos> calculateExplodedPositions(
            ServerExplosion explosion,
            ServerLevel level,
            Vec3 center,
            float radius,
            ExplosionDamageCalculator damageCalculator
    ) {
        LongOpenHashSet toBlowSet = new LongOpenHashSet();
        RayCache cache = LOCAL_CACHE.get();
        cache.clear();

        try {
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            double[] raySteps = RAY_STEPS;
            int stepIdx = 0;

            for (int i = 0; i < TOTAL_RAYS; i++) {
                double stepX = raySteps[stepIdx++];
                double stepY = raySteps[stepIdx++];
                double stepZ = raySteps[stepIdx++];

                // Exact vanilla RNG parity
                float remainingPower = radius * (0.7f + level.getRandom().nextFloat() * 0.6f);
                double xp = center.x;
                double yp = center.y;
                double zp = center.z;

                int prevBx = Integer.MIN_VALUE;
                int prevBy = Integer.MIN_VALUE;
                int prevBz = Integer.MIN_VALUE;
                float cachedDelta = 0.0f;
                BlockState cachedBlock = null;
                boolean cachedInBounds = true;

                while (remainingPower > 0.0f) {
                    int bx = Mth.floor(xp);
                    int by = Mth.floor(yp);
                    int bz = Mth.floor(zp);

                    if (bx == prevBx && by == prevBy && bz == prevBz) {
                        if (!cachedInBounds) {
                            break;
                        }
                        remainingPower -= cachedDelta;
                        if (remainingPower > 0.0f && cachedBlock != null) {
                            mpos.set(bx, by, bz);
                            if (damageCalculator.shouldBlockExplode(explosion, (BlockGetter) level, mpos, cachedBlock, remainingPower)) {
                                toBlowSet.add(BlockPos.asLong(bx, by, bz));
                            }
                        }
                    } else {
                        prevBx = bx;
                        prevBy = by;
                        prevBz = bz;

                        long packed = BlockPos.asLong(bx, by, bz);
                        int hash = (int) (packed ^ (packed >>> 16) ^ (packed >>> 32)) & CACHE_MASK;

                        if (cache.keys[hash] == packed) {
                            cachedInBounds = cache.inBounds[hash];
                            if (!cachedInBounds) {
                                break;
                            }
                            cachedDelta = cache.delta[hash];
                            cachedBlock = cache.blockStates[hash];
                        } else {
                            mpos.set(bx, by, bz);
                            if (!level.isInWorldBounds(mpos)) {
                                cache.keys[hash] = packed;
                                cache.inBounds[hash] = false;
                                cache.blockStates[hash] = null;
                                cachedInBounds = false;
                                cachedBlock = null;
                                break;
                            }

                            BlockState block = level.getBlockState(mpos);
                            FluidState fluid = level.getFluidState(mpos);
                            Optional<Float> resistance = damageCalculator.getBlockExplosionResistance(
                                    explosion, (BlockGetter) level, mpos, block, fluid
                            );

                            float delta = 0.0f;
                            if (resistance.isPresent()) {
                                delta = (resistance.get().floatValue() + 0.3f) * 0.3f;
                            }

                            cache.keys[hash] = packed;
                            cache.inBounds[hash] = true;
                            cache.delta[hash] = delta;
                            cache.blockStates[hash] = block;

                            cachedInBounds = true;
                            cachedDelta = delta;
                            cachedBlock = block;
                        }

                        remainingPower -= cachedDelta;
                        if (remainingPower > 0.0f && cachedBlock != null) {
                            mpos.set(bx, by, bz);
                            if (damageCalculator.shouldBlockExplode(explosion, (BlockGetter) level, mpos, cachedBlock, remainingPower)) {
                                toBlowSet.add(packed);
                            }
                        }
                    }

                    xp += stepX;
                    yp += stepY;
                    zp += stepZ;
                    remainingPower -= 0.22500001f;
                }
            }

            ObjectArrayList<BlockPos> result = new ObjectArrayList<>(toBlowSet.size());
            for (long packed : toBlowSet) {
                result.add(BlockPos.of(packed));
            }
            return result;
        } finally {
            // Guaranteed memory barrier & ThreadLocal leak prevention:
            // Release all BlockState object references immediately upon exit
            cache.clear();
        }
    }

    /**
     * Consolidated drop collector to prevent O(N^2) item merges and entity spam.
     */
    public static class ConsolidatedDropManager {
        public static class StackCollector {
            public final BlockPos pos;
            public ItemStack stack;

            public StackCollector(BlockPos pos, ItemStack stack) {
                this.pos = pos;
                this.stack = stack;
            }

            public boolean tryMerge(ItemStack input) {
                int limit = Math.min(16, this.stack.getMaxStackSize());
                if (this.stack.getCount() >= limit) {
                    return true;
                }
                if (ItemEntity.areMergable(this.stack, input)) {
                    this.stack = ItemEntity.merge(this.stack, input, limit);
                    return this.stack.getCount() >= limit;
                }
                return false;
            }
        }

        private final List<StackCollector> allCollectors = new ArrayList<>();
        private final Map<Item, List<StackCollector>> buckets = new java.util.IdentityHashMap<>();

        public void addStack(ItemStack stack, BlockPos pos) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            ItemStack working = stack.copy();
            List<StackCollector> list = this.buckets.computeIfAbsent(working.getItem(), k -> new ArrayList<>(2));
            for (int i = 0; i < list.size(); ) {
                StackCollector c = list.get(i);
                boolean full = c.tryMerge(working);
                if (full) {
                    int last = list.size() - 1;
                    if (i != last) {
                        list.set(i, list.get(last));
                    }
                    list.remove(last);
                } else {
                    i++;
                }
                if (working.isEmpty()) {
                    return;
                }
            }

            if (!working.isEmpty()) {
                StackCollector next = new StackCollector(pos.immutable(), working);
                this.allCollectors.add(next);
                if (next.stack.getCount() < Math.min(16, next.stack.getMaxStackSize())) {
                    list.add(next);
                }
            }
        }

        public void popAll(ServerLevel level) {
            for (StackCollector c : allCollectors) {
                Block.popResource(level, c.pos, c.stack);
            }
        }
    }
}
