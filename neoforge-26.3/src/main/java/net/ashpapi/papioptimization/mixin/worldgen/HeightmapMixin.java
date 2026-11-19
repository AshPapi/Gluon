package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.function.Predicate;

@Mixin(value = Heightmap.class, priority = 1500)
public abstract class HeightmapMixin {

    @Inject(method = "primeHeightmaps", at = @At("HEAD"), cancellable = true)
    private static void papi$fastPrimeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types, CallbackInfo ci) {
        if (types == null || types.isEmpty()) {
            ci.cancel();
            return;
        }

        int typeCount = types.size();
        if (typeCount >= 31) {
            return;
        }
        Heightmap[] heightmaps = new Heightmap[typeCount];
        @SuppressWarnings("unchecked")
        Predicate<BlockState>[] predicates = (Predicate<BlockState>[]) new Predicate<?>[typeCount];

        int idx = 0;
        for (Heightmap.Types type : types) {
            heightmaps[idx] = chunk.getOrCreateHeightmapUnprimed(type);
            predicates[idx] = type.isOpaque();
            idx++;
        }

        int allMask = (1 << typeCount) - 1;
        int[] pendingMask = new int[256];
        for (int i = 0; i < 256; i++) {
            pendingMask[i] = allMask;
        }

        int remainingColumns = 256;
        LevelChunkSection[] sections = chunk.getSections();
        int highestFilled = chunk.getHighestFilledSectionIndex();
        if (highestFilled == -1) {
            ci.cancel();
            return;
        }

        int minSectionY = chunk.getMinSectionY();

        for (int sIdx = highestFilled; sIdx >= 0; sIdx--) {
            LevelChunkSection section = sections[sIdx];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            int sectionBaseY = (sIdx + minSectionY) << 4;

            for (int dy = 15; dy >= 0; dy--) {
                int worldY = sectionBaseY + dy;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int col = x | (z << 4);
                        int mask = pendingMask[col];
                        if (mask == 0) continue;

                        BlockState state = section.getBlockState(x, dy, z);
                        if (state.isAir()) continue;

                        for (int h = 0; h < typeCount; h++) {
                            int bit = 1 << h;
                            if ((mask & bit) != 0 && predicates[h].test(state)) {
                                ((HeightmapInvoker) heightmaps[h]).papi$invokeSetHeight(x, z, worldY + 1);
                                mask &= ~bit;
                            }
                        }

                        pendingMask[col] = mask;
                        if (mask == 0) {
                            remainingColumns--;
                            if (remainingColumns == 0) {
                                ci.cancel();
                                return;
                            }
                        }
                    }
                }
            }
        }

        ci.cancel();
    }
}
