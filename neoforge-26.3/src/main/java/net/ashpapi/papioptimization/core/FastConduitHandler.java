package net.ashpapi.papioptimization.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * PapiOptimization Fast Conduit Handler:
 * - Precomputed 42 prismarine frame offsets: eliminates 125 3D-loop iterations and math checks.
 * - Zero-allocation water and frame checks using a single MutableBlockPos.
 * - Ultra-fast O(P) direct player iteration instead of scanning 4,056 chunk sections.
 * - 100% exact vanilla parity in range, effect duration, and target criteria.
 */
public final class FastConduitHandler {

    private FastConduitHandler() {}

    // Exactly 42 precomputed frame offsets (3 intersecting 5x5 rings)
    private static final int[] FRAME_DX = {
        -2, -2, -2, -2, -2, -2, -2, -2, -2,
        -1, -1, -1, -1,
         0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
         1,  1,  1,  1,
         2,  2,  2,  2,  2,  2,  2,  2,  2
    };

    private static final int[] FRAME_DY = {
        -2, -1,  0,  0,  0,  0,  0,  1,  2,
        -2,  0,  0,  2,
        -2, -2, -2, -2, -2, -1, -1,  0,  0,  1,  1,  2,  2,  2,  2,  2,
        -2,  0,  0,  2,
        -2, -1,  0,  0,  0,  0,  0,  1,  2
    };

    private static final int[] FRAME_DZ = {
         0,  0, -2, -1,  0,  1,  2,  0,  0,
         0, -2,  2,  0,
        -2, -1,  0,  1,  2, -2,  2, -2,  2, -2,  2, -2, -1,  0,  1,  2,
         0, -2,  2,  0,
         0,  0, -2, -1,  0,  1,  2,  0,  0
    };

    static {
        if (FRAME_DX.length != 42 || FRAME_DY.length != 42 || FRAME_DZ.length != 42) {
            throw new IllegalStateException("Conduit frame offset arrays must all have length 42");
        }
    }

    /**
     * Optimized conduit shape check:
     * - Checks 3x3x3 water cage (27 blocks) with 0 allocations.
     * - Checks 42 frame blocks directly without 125 cube iterations.
     */
    public static boolean updateShapeFast(Level level, BlockPos worldPosition, List<BlockPos> effectBlocks) {
        effectBlocks.clear();
        int px = worldPosition.getX();
        int py = worldPosition.getY();
        int pz = worldPosition.getZ();

        int mark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos mut = BlockPosPool.get();

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        mut.set(px + ox, py + oy, pz + oz);
                        if (!level.isWaterAt(mut)) {
                            return false;
                        }
                    }
                }
            }

            for (int i = 0; i < 42; i++) {
                mut.set(px + FRAME_DX[i], py + FRAME_DY[i], pz + FRAME_DZ[i]);
                BlockState state = level.getBlockState(mut);
                if (state.is(BlockTags.CONDUIT_EFFECT_BLOCK)) {
                    effectBlocks.add(mut.immutable());
                }
            }

            return effectBlocks.size() >= 16;
        } finally {
            BlockPosPool.reset(mark);
        }
    }

    /**
     * Optimized conduit effect application:
     * - Iterates dimension player list directly in O(P) time instead of querying
     *   all entity chunk sections across a 193x193x384 bounding volume (~4,056 sections).
     * - Checks closerThan and isInWaterOrRain with 100% vanilla parity.
     */
    public static void applyEffectsFast(Level level, BlockPos worldPosition, List<BlockPos> effectBlocks) {
        int activeSize = effectBlocks.size();
        int effectRange = activeSize / 7 * 16;
        if (effectRange <= 0) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ServerPlayer> players = serverLevel.players();
        if (players.isEmpty()) {
            return;
        }

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (worldPosition.closerThan(player.blockPosition(), (double) effectRange) && player.isInWaterOrRain()) {
                player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true));
            }
        }
    }
}
