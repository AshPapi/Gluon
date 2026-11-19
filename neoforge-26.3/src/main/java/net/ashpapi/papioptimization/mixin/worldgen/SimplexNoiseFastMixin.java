package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.GradientNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SimplexNoise.class, priority = 1500)
public abstract class SimplexNoiseFastMixin extends GradientNoise {

    @Unique
    private static final double PAPI$F2 = 0.5 * (Math.sqrt(3.0) - 1.0);

    @Unique
    private static final double PAPI$G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    @Unique
    private static final double[] PAPI$FLAT_GRAD_D = {
        1.0, 1.0, 0.0, 0.0,
        -1.0, 1.0, 0.0, 0.0,
        1.0, -1.0, 0.0, 0.0,
        -1.0, -1.0, 0.0, 0.0,
        1.0, 0.0, 1.0, 0.0,
        -1.0, 0.0, 1.0, 0.0,
        1.0, 0.0, -1.0, 0.0,
        -1.0, 0.0, -1.0, 0.0,
        0.0, 1.0, 1.0, 0.0,
        0.0, -1.0, 1.0, 0.0,
        0.0, 1.0, -1.0, 0.0,
        0.0, -1.0, -1.0, 0.0,
        1.0, 1.0, 0.0, 0.0,
        0.0, -1.0, 1.0, 0.0,
        -1.0, 1.0, 0.0, 0.0,
        0.0, -1.0, -1.0, 0.0
    };

    @Unique
    private static final int[] PAPI$MOD12_SHIFT2 = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            PAPI$MOD12_SHIFT2[i] = (i % 12) << 2;
        }
    }

    protected SimplexNoiseFastMixin(RandomSource randomSource) {
        super(randomSource);
    }

    @Inject(method = "getCornerNoise3D", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetCornerNoise3D(
        int gradIndex, double dx, double dy, double dz, double falloff,
        CallbackInfoReturnable<Double> cir
    ) {
        double t = falloff - dx * dx - dy * dy - dz * dz;
        if (t < 0.0) {
            cir.setReturnValue(0.0);
            return;
        }
        t *= t;
        int idx = (gradIndex & 15) << 2;
        double[] g = PAPI$FLAT_GRAD_D;
        double dot = g[idx] * dx + g[idx + 1] * dy + g[idx + 2] * dz;
        cir.setReturnValue(t * t * dot);
    }

    @Inject(method = "get(DD)F", at = @At("HEAD"), cancellable = true)
    private void papi$fastGet2D(double x, double y, CallbackInfoReturnable<Float> cir) {
        double xin = x + this.offsetX;
        double yin = y + this.offsetY;
        double s = (xin + yin) * PAPI$F2;
        int i = Mth.floor(xin + s);
        int j = Mth.floor(yin + s);
        double t = (double) (i + j) * PAPI$G2;
        double x0 = xin - ((double) i - t);
        double y0 = yin - ((double) j - t);

        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - (double) i1 + PAPI$G2;
        double y1 = y0 - (double) j1 + PAPI$G2;
        double x2 = x0 - 1.0 + 2.0 * PAPI$G2;
        double y2 = y0 - 1.0 + 2.0 * PAPI$G2;

        byte[] p = this.perms;
        int ii = i & 255;
        int jj = j & 255;
        int pj0 = p[jj] & 255;
        int pj1 = p[(jj + j1) & 255] & 255;
        int pj2 = p[(jj + 1) & 255] & 255;

        int gi0 = PAPI$MOD12_SHIFT2[p[(ii + pj0) & 255] & 255];
        int gi1 = PAPI$MOD12_SHIFT2[p[(ii + i1 + pj1) & 255] & 255];
        int gi2 = PAPI$MOD12_SHIFT2[p[(ii + 1 + pj2) & 255] & 255];

        double[] g = PAPI$FLAT_GRAD_D;
        double n0 = 0.0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 >= 0.0) {
            t0 *= t0;
            n0 = t0 * t0 * (g[gi0] * x0 + g[gi0 + 1] * y0 + g[gi0 + 2] * 0.0);
        }

        double n1 = 0.0;
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 >= 0.0) {
            t1 *= t1;
            n1 = t1 * t1 * (g[gi1] * x1 + g[gi1 + 1] * y1 + g[gi1 + 2] * 0.0);
        }

        double n2 = 0.0;
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 >= 0.0) {
            t2 *= t2;
            n2 = t2 * t2 * (g[gi2] * x2 + g[gi2 + 1] * y2 + g[gi2 + 2] * 0.0);
        }

        cir.setReturnValue((float) (70.0 * (n0 + n1 + n2)));
    }
}
