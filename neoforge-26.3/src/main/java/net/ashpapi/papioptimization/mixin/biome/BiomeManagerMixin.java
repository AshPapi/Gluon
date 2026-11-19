package net.ashpapi.papioptimization.mixin.biome;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BiomeManager.class, priority = 1500)
public abstract class BiomeManagerMixin {

    @Shadow @Final private BiomeResolver noiseBiomeSource;
    @Shadow @Final private long biomeZoomSeed;

    @Shadow
    private static double getFiddledDistance(long zoomSeed, int px, int py, int pz, double dx, double dy, double dz) {
        throw new AssertionError();
    }

    @WrapMethod(method = "getBiome(III)Lnet/minecraft/core/Holder;")
    private Holder<Biome> papi$getBiome(int x, int y, int z, Operation<Holder<Biome>> original) {
        int i = x - 2;
        int j = y - 2;
        int k = z - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;

        double d0 = (double) (i & 3) * 0.25;
        double d1 = (double) (j & 3) * 0.25;
        double d2 = (double) (k & 3) * 0.25;
        int k1 = 0;
        double d3 = Double.POSITIVE_INFINITY;

        for (int l1 = 0; l1 < 8; ++l1) {
            boolean flag = (l1 & 4) == 0;
            boolean flag1 = (l1 & 2) == 0;
            boolean flag2 = (l1 & 1) == 0;
            int i2 = flag ? l : l + 1;
            int j2 = flag1 ? i1 : i1 + 1;
            int k2 = flag2 ? j1 : j1 + 1;
            double d4 = flag ? d0 : d0 - 1.0;
            double d5 = flag1 ? d1 : d1 - 1.0;
            double d6 = flag2 ? d2 : d2 - 1.0;
            double d7 = getFiddledDistance(this.biomeZoomSeed, i2, j2, k2, d4, d5, d6);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }

        int winX = (k1 & 4) == 0 ? l : l + 1;
        int winY = (k1 & 2) == 0 ? i1 : i1 + 1;
        int winZ = (k1 & 1) == 0 ? j1 : j1 + 1;
        return this.noiseBiomeSource.getNoiseBiome(winX, winY, winZ);
    }

    @WrapMethod(method = "getFiddle")
    private static double papi$getFiddle(long seed, Operation<Double> original) {
        double d0 = (double) ((int) (seed >> 24) & 1023) * 0.0009765625;
        return (d0 - 0.5) * 0.9;
    }
}
