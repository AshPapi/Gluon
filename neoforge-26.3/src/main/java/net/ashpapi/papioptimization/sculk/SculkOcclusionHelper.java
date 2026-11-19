package net.ashpapi.papioptimization.sculk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

public final class SculkOcclusionHelper {
    private SculkOcclusionHelper() {}

    /**
     * Highly optimized wool occlusion check with 100% vanilla parity.
     * 1. First tests chunk section palettes across the trajectory bounding box.
     *    If no section contains blocks tagged with OCCLUDES_VIBRATION_SIGNALS (wool),
     *    no ray can ever hit wool, avoiding all 6 raycasts entirely (O(1) palette check).
     * 2. If wool is present in any section, performs an inlined, zero-allocation DDA raycast
     *    in all 6 nudged directions. As soon as one direction is unblocked, returns false immediately.
     */
    public static boolean isOccluded(Level level, Vec3 origin, Vec3 dest) {
        int fromBlockX = Mth.floor(origin.x);
        int fromBlockY = Mth.floor(origin.y);
        int fromBlockZ = Mth.floor(origin.z);

        int toBlockX = Mth.floor(dest.x);
        int toBlockY = Mth.floor(dest.y);
        int toBlockZ = Mth.floor(dest.z);

        if (fromBlockX == toBlockX && fromBlockY == toBlockY && fromBlockZ == toBlockZ) {
            int mark = net.ashpapi.papioptimization.core.BlockPosPool.mark();
            try {
                return level.getBlockState(net.ashpapi.papioptimization.core.BlockPosPool.get(fromBlockX, fromBlockY, fromBlockZ))
                        .is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
            } finally {
                net.ashpapi.papioptimization.core.BlockPosPool.reset(mark);
            }
        }

        // Palette pre-check: check all chunk sections intersecting the AABB
        int minBlockX = Math.min(fromBlockX, toBlockX);
        int maxBlockX = Math.max(fromBlockX, toBlockX);
        int minBlockY = Math.max(level.getMinY(), Math.min(fromBlockY, toBlockY));
        int maxBlockY = Math.min(level.getMaxY(), Math.max(fromBlockY, toBlockY));
        int minBlockZ = Math.min(fromBlockZ, toBlockZ);
        int maxBlockZ = Math.max(fromBlockZ, toBlockZ);

        int minSecX = SectionPos.blockToSectionCoord(minBlockX);
        int maxSecX = SectionPos.blockToSectionCoord(maxBlockX);
        int minSecY = SectionPos.blockToSectionCoord(minBlockY);
        int maxSecY = SectionPos.blockToSectionCoord(maxBlockY);
        int minSecZ = SectionPos.blockToSectionCoord(minBlockZ);
        int maxSecZ = SectionPos.blockToSectionCoord(maxBlockZ);

        boolean hasAnyWool = false;
        checkSections:
        for (int cx = minSecX; cx <= maxSecX; ++cx) {
            for (int cz = minSecZ; cz <= maxSecZ; ++cz) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int cy = minSecY; cy <= maxSecY; ++cy) {
                    int secIdx = chunk.getSectionIndexFromSectionY(cy);
                    if (secIdx >= 0 && secIdx < chunk.getSections().length) {
                        LevelChunkSection section = chunk.getSections()[secIdx];
                        if (section != null && !section.hasOnlyAir() && section.maybeHas(s -> s.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))) {
                            hasAnyWool = true;
                            break checkSections;
                        }
                    }
                }
            }
        }

        // If no chunk section along the path contains any wool, occlusion is impossible!
        if (!hasAnyWool) {
            return false;
        }

        // Fast zero-allocation DDA raycast for the 6 nudged directions
        double centerXFrom = fromBlockX + 0.5;
        double centerYFrom = fromBlockY + 0.5;
        double centerZFrom = fromBlockZ + 0.5;

        double centerXTo = toBlockX + 0.5;
        double centerYTo = toBlockY + 0.5;
        double centerZTo = toBlockZ + 0.5;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            double startX = centerXFrom + direction.getStepX() * 1.0E-5;
            double startY = centerYFrom + direction.getStepY() * 1.0E-5;
            double startZ = centerZFrom + direction.getStepZ() * 1.0E-5;

            if (!fastRayHitWool(level, startX, startY, startZ, centerXTo, centerYTo, centerZTo, mutablePos)) {
                // If ANY ray reaches target without hitting wool, vibration is NOT occluded!
                return false;
            }
        }

        // All 6 directions were blocked by wool
        return true;
    }

    private static boolean fastRayHitWool(Level level, double fromX, double fromY, double fromZ,
                                          double toX, double toY, double toZ,
                                          BlockPos.MutableBlockPos pos) {
        if (fromX == toX && fromY == toY && fromZ == toZ) {
            return false;
        }

        double toX1 = Mth.lerp(-1.0E-7, toX, fromX);
        double toY1 = Mth.lerp(-1.0E-7, toY, fromY);
        double toZ1 = Mth.lerp(-1.0E-7, toZ, fromZ);
        double fromX1 = Mth.lerp(-1.0E-7, fromX, toX);
        double fromY1 = Mth.lerp(-1.0E-7, fromY, toY);
        double fromZ1 = Mth.lerp(-1.0E-7, fromZ, toZ);

        int currentBlockX = Mth.floor(fromX1);
        int currentBlockY = Mth.floor(fromY1);
        int currentBlockZ = Mth.floor(fromZ1);

        pos.set(currentBlockX, currentBlockY, currentBlockZ);
        if (level.getBlockState(pos).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) {
            return true;
        }

        double dx = toX1 - fromX1;
        double dy = toY1 - fromY1;
        double dz = toZ1 - fromZ1;

        int signX = Mth.sign(dx);
        int signY = Mth.sign(dy);
        int signZ = Mth.sign(dz);

        double tDeltaX = signX == 0 ? Double.MAX_VALUE : (double)signX / dx;
        double tDeltaY = signY == 0 ? Double.MAX_VALUE : (double)signY / dy;
        double tDeltaZ = signZ == 0 ? Double.MAX_VALUE : (double)signZ / dz;

        double tX = tDeltaX * (signX > 0 ? 1.0 - Mth.frac(fromX1) : Mth.frac(fromX1));
        double tY = tDeltaY * (signY > 0 ? 1.0 - Mth.frac(fromY1) : Mth.frac(fromY1));
        double tZ = tDeltaZ * (signZ > 0 ? 1.0 - Mth.frac(fromZ1) : Mth.frac(fromZ1));

        while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
            if (tX < tY) {
                if (tX < tZ) {
                    currentBlockX += signX;
                    tX += tDeltaX;
                } else {
                    currentBlockZ += signZ;
                    tZ += tDeltaZ;
                }
            } else if (tY < tZ) {
                currentBlockY += signY;
                tY += tDeltaY;
            } else {
                currentBlockZ += signZ;
                tZ += tDeltaZ;
            }

            pos.set(currentBlockX, currentBlockY, currentBlockZ);
            if (level.getBlockState(pos).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) {
                return true;
            }
        }

        return false;
    }
}
