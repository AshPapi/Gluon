package net.ashpapi.papioptimization.core;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * PapiOptimization Fast Copper Oxidizer:
 * - Replaces Manhattan stream / lambda pipeline with zero-allocation imperative scan.
 * - Early aborts instantly if a younger copper block neighbor exists.
 * - Chunk caching when scanning within the same 16x16 chunk column.
 * - 100% exact vanilla parity in neighbor counting, chance formulas, and RNG sampling.
 */
public final class FastCopperOxidizer {

    private FastCopperOxidizer() {}

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> Optional<BlockState> getNextStateFast(
            ChangeOverTimeBlock<T> block,
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random) {

        int ownAge = block.getAge().ordinal();
        Class<?> ageClass = block.getAge().getDeclaringClass();
        int olderCount = 0;
        int sameAgeCount = 0;

        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        int minChunkX = (posX - 4) >> 4;
        int maxChunkX = (posX + 4) >> 4;
        int minChunkZ = (posZ - 4) >> 4;
        int maxChunkZ = (posZ + 4) >> 4;

        ChunkAccess cachedChunk = (minChunkX == maxChunkX && minChunkZ == maxChunkZ)
                ? level.getChunk(posX >> 4, posZ >> 4)
                : null;

        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        for (int dx = -4; dx <= 4; dx++) {
            int maxDy = 4 - Math.abs(dx);
            for (int dy = -maxDy; dy <= maxDy; dy++) {
                int maxDz = maxDy - Math.abs(dy);
                for (int dz = -maxDz; dz <= maxDz; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    mutPos.set(posX + dx, posY + dy, posZ + dz);
                    BlockState neighborState = (cachedChunk != null)
                            ? cachedChunk.getBlockState(mutPos)
                            : level.getBlockState(mutPos);

                    Block neighborBlock = neighborState.getBlock();
                    if (neighborBlock instanceof ChangeOverTimeBlock<?> cotb) {
                        if (cotb.getAge().getDeclaringClass() == ageClass) {
                            int foundAge = ((Enum<?>) cotb.getAge()).ordinal();
                            if (foundAge < ownAge) {
                                // In vanilla: if foundAge < ownAge, abort completely!
                                return Optional.empty();
                            }
                            if (foundAge > ownAge) {
                                olderCount++;
                            } else {
                                sameAgeCount++;
                            }
                        }
                    }
                }
            }
        }

        float chance = (float) (olderCount + 1) / (float) (olderCount + sameAgeCount + 1);
        float actualChance = chance * chance * block.getChanceModifier();
        return random.nextFloat() < actualChance ? block.getNext(state) : Optional.empty();
    }
}
