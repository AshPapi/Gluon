package net.ashpapi.papioptimization.math;

import net.minecraft.util.RandomSource;

public final class FastRNG {

    private static final int ZIG_LAYERS = 128;
    private static final double ZIG_R = 3.442619855899;
    private static final double ZIG_V = 9.91256303526217e-3;
    private static final double INV_R = 1.0 / ZIG_R;
    private static final double SCALE_56 = (double) (1L << 56);
    private static final double INV_2_56 = 0x1.0p-56;
    private static final double INV_2_53 = 0x1.0p-53;

    private static final double[] ZIG_W = new double[ZIG_LAYERS];
    private static final double[] ZIG_F = new double[ZIG_LAYERS + 1];
    private static final long[] ZIG_K = new long[ZIG_LAYERS];

    static {
        double[] zigX = new double[ZIG_LAYERS + 1];
        double f = Math.exp(-0.5 * ZIG_R * ZIG_R);
        zigX[0] = ZIG_V / f;
        ZIG_F[0] = 0.0;
        zigX[1] = ZIG_R;
        ZIG_F[1] = f;

        for (int i = 2; i < ZIG_LAYERS; i++) {
            double fNext = ZIG_F[i - 1] + ZIG_V / zigX[i - 1];
            zigX[i] = Math.sqrt(-2.0 * Math.log(fNext));
            ZIG_F[i] = fNext;
        }
        zigX[ZIG_LAYERS] = 0.0;
        ZIG_F[ZIG_LAYERS] = 1.0;

        for (int i = 0; i < ZIG_LAYERS; i++) {
            ZIG_W[i] = zigX[i] * INV_2_56;
            ZIG_K[i] = (long) Math.floor((zigX[i + 1] / zigX[i]) * SCALE_56);
        }
    }

    private FastRNG() {}

    public static double nextGaussian(RandomSource source) {
        while (true) {
            long u = source.nextLong();
            int idx = (int) (u & 127L);
            long signBit = u & Long.MIN_VALUE;
            long mag = (u >>> 7) & 0x00FFFFFFFFFFFFFFL;

            if (mag < ZIG_K[idx]) {
                return applySign(mag * ZIG_W[idx], signBit);
            }

            if (idx == 0) {
                double x;
                double y;
                do {
                    double u1 = ((source.nextLong() >>> 11) + 0.5) * INV_2_53;
                    double u2 = ((source.nextLong() >>> 11) + 0.5) * INV_2_53;
                    x = -Math.log(u1) * INV_R;
                    y = -Math.log(u2);
                } while (y + y < x * x);
                return applySign(ZIG_R + x, signBit);
            }

            double x = mag * ZIG_W[idx];
            double uy = ((source.nextLong() >>> 11) + 0.5) * INV_2_53;
            double fLow = ZIG_F[idx];
            double y = fLow + uy * (ZIG_F[idx + 1] - fLow);
            if (y < Math.exp(-0.5 * x * x)) {
                return applySign(x, signBit);
            }
        }
    }

    private static double applySign(double magnitude, long signBit) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(magnitude) | signBit);
    }
}
