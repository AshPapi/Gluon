package net.ashpapi.papioptimization.mixin.biome;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import net.ashpapi.papioptimization.client.PapiBiomeBlendState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelBiomeBlendMixin {

    @Unique
    private static final ThreadLocal<PapiBiomeBlendState> PAPI$BLEND_STATE =
            ThreadLocal.withInitial(PapiBiomeBlendState::new);

    @Unique
    private static boolean papi$isPositionInvariant(ColorResolver resolver, Biome biome) {
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER || resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            return true;
        }
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) {
            return biome.getSpecialEffects().grassColorModifier() == BiomeSpecialEffects.GrassColorModifier.NONE;
        }
        return false;
    }

    @WrapMethod(method = "calculateBlockTint")
    private int papi$calculateBlockTint(BlockPos pos, ColorResolver colorResolver, Operation<Integer> original) {
        ClientLevel self = (ClientLevel) (Object) this;
        int dist = Minecraft.getInstance().options.biomeBlendRadius().get();
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        Holder<Biome> centerBiomeHolder = self.getBiome(pos);
        Biome centerBiome = centerBiomeHolder.value();

        if (dist <= 0) {
            return colorResolver.getColor(centerBiome, posX, posZ);
        }

        PapiBiomeBlendState state = PAPI$BLEND_STATE.get();
        BlockPos.MutableBlockPos samplePos = state.samplePos;

        boolean isUniform = self.getBiome(samplePos.set(posX - dist, posY, posZ - dist)) == centerBiomeHolder
                && self.getBiome(samplePos.set(posX + dist, posY, posZ - dist)) == centerBiomeHolder
                && self.getBiome(samplePos.set(posX - dist, posY, posZ + dist)) == centerBiomeHolder
                && self.getBiome(samplePos.set(posX + dist, posY, posZ + dist)) == centerBiomeHolder;

        if (isUniform) {
            int maxX = posX + dist;
            int maxZ = posZ + dist;
            uniformScan:
            for (int z = posZ - dist; z <= maxZ; z++) {
                for (int x = posX - dist; x <= maxX; x++) {
                    if (self.getBiome(samplePos.set(x, posY, z)) != centerBiomeHolder) {
                        isUniform = false;
                        break uniformScan;
                    }
                }
            }
        }

        if (isUniform && papi$isPositionInvariant(colorResolver, centerBiome)) {
            return colorResolver.getColor(centerBiome, posX, posZ);
        }

        int count = (dist * 2 + 1) * (dist * 2 + 1);
        int totalRed = 0;
        int totalGreen = 0;
        int totalBlue = 0;

        Biome[] paletteBiomes = state.paletteBiomes;
        int[] paletteCounts = state.paletteCounts;
        int paletteSize = 0;

        try {
            for (int z = posZ - dist; z <= posZ + dist; z++) {
                for (int x = posX - dist; x <= posX + dist; x++) {
                    samplePos.set(x, posY, z);
                    Biome biome = self.getBiome(samplePos).value();

                    if (!papi$isPositionInvariant(colorResolver, biome)) {
                        int color = colorResolver.getColor(biome, x, z);
                        totalRed += ARGB.red(color);
                        totalGreen += ARGB.green(color);
                        totalBlue += ARGB.blue(color);
                    } else {
                        int foundIndex = -1;
                        for (int i = 0; i < paletteSize; i++) {
                            if (paletteBiomes[i] == biome) {
                                foundIndex = i;
                                break;
                            }
                        }
                        if (foundIndex != -1) {
                            paletteCounts[foundIndex]++;
                        } else if (paletteSize < 8) {
                            paletteBiomes[paletteSize] = biome;
                            paletteCounts[paletteSize] = 1;
                            paletteSize++;
                        } else {
                            int color = colorResolver.getColor(biome, x, z);
                            totalRed += ARGB.red(color);
                            totalGreen += ARGB.green(color);
                            totalBlue += ARGB.blue(color);
                        }
                    }
                }
            }

            for (int i = 0; i < paletteSize; i++) {
                int weight = paletteCounts[i];
                int color = colorResolver.getColor(paletteBiomes[i], posX, posZ);
                totalRed += ARGB.red(color) * weight;
                totalGreen += ARGB.green(color) * weight;
                totalBlue += ARGB.blue(color) * weight;
            }
        } finally {
            Arrays.fill(paletteBiomes, 0, paletteSize, null);
        }

        return ARGB.color(totalRed / count, totalGreen / count, totalBlue / count);
    }
}
