package net.ashpapi.papioptimization.core;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * PapiOptimization Fast Beacon Handler:
 * - High-speed zero-allocation beacon base verification with chunk caching.
 * - Ultra-fast O(P) direct player iteration instead of scanning 1,176 chunk sections.
 * - 100% exact vanilla parity in radiuses, durations, amplifiers, and timings.
 */
public final class FastBeaconHandler {

    private FastBeaconHandler() {}

    /**
     * Optimized pyramid verification:
     * - Reuses a single MutableBlockPos (0 allocations vs 164 allocations in vanilla).
     * - Caches chunk reference for pyramid blocks within the same chunk.
     * - Early-exit if layer 1 fails (skips 155 checks).
     */
    public static int updateBaseFast(Level level, int x, int y, int z) {
        int minY = level.getMinY();
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        int levels = 0;

        int currentChunkX = x >> 4;
        int currentChunkZ = z >> 4;
        ChunkAccess cachedChunk = level.getChunkSource().getChunk(currentChunkX, currentChunkZ, false);
        if (cachedChunk == null) {
            return 0;
        }

        for (int step = 1; step <= 4; step++) {
            int ly = y - step;
            if (ly < minY) {
                break;
            }
            boolean isOk = true;
            for (int lx = x - step; lx <= x + step; lx++) {
                int cx = lx >> 4;
                for (int lz = z - step; lz <= z + step; lz++) {
                    int cz = lz >> 4;
                    if (cx != currentChunkX || cz != currentChunkZ) {
                        currentChunkX = cx;
                        currentChunkZ = cz;
                        cachedChunk = level.getChunkSource().getChunk(cx, cz, false);
                    }
                    if (cachedChunk == null) {
                        isOk = false;
                        break;
                    }
                    BlockState state = cachedChunk.getBlockState(mutPos.set(lx, ly, lz));
                    if (!state.is(BlockTags.BEACON_BASE_BLOCKS)) {
                        isOk = false;
                        break;
                    }
                }
                if (!isOk) {
                    break;
                }
            }
            if (!isOk) {
                break;
            }
            levels = step;
        }
        return levels;
    }

    /**
     * Optimized effect application:
     * - Iterates dimension player list directly in O(P) time instead of querying
     *   all entity chunk sections across a 101x101x384 bounding volume (~1,176 sections).
     * - Direct coordinate range checks (0 allocations).
     * - Single pass handles both primary and secondary effects.
     */
    public static void applyEffectsFast(
            Level level, BlockPos worldPosition, int levels,
            Holder<MobEffect> primaryPower,
            Holder<MobEffect> secondaryPower) {
        if (level.isClientSide() || primaryPower == null) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<ServerPlayer> players = serverLevel.players();
        if (players.isEmpty()) {
            return;
        }

        double range = levels * 10 + 10;
        int baseAmp = (levels >= 4 && Objects.equals(primaryPower, secondaryPower)) ? 1 : 0;
        int durationTicks = (9 + levels * 2) * 20;

        double minX = (double) worldPosition.getX() - range;
        double minY = (double) worldPosition.getY() - range;
        double minZ = (double) worldPosition.getZ() - range;
        double maxX = (double) worldPosition.getX() + 1.0 + range;
        double maxY = (double) worldPosition.getY() + 1.0 + range + (double) level.getHeight();
        double maxZ = (double) worldPosition.getZ() + 1.0 + range;

        boolean hasSecondary = (levels >= 4 && !Objects.equals(primaryPower, secondaryPower) && secondaryPower != null);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (player.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                player.addEffect(new MobEffectInstance(primaryPower, durationTicks, baseAmp, true, true));
                if (hasSecondary) {
                    player.addEffect(new MobEffectInstance(secondaryPower, durationTicks, 0, true, true));
                }
            }
        }
    }
}
