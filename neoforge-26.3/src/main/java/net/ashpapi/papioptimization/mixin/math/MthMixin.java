package net.ashpapi.papioptimization.mixin.math;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.math.FastMath;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Mth.class, priority = 1500)
public abstract class MthMixin {

    @WrapMethod(method = "sin")
    private static float papi$sin(double rad, Operation<Float> original) {
        return FastMath.sin(rad);
    }

    @WrapMethod(method = "cos")
    private static float papi$cos(double rad, Operation<Float> original) {
        return FastMath.cos(rad);
    }

    @WrapMethod(method = "atan2")
    private static double papi$atan2(double y, double x, Operation<Double> original) {
        return FastMath.atan2(y, x);
    }

    @WrapMethod(method = "wrapDegrees(F)F")
    private static float papi$wrapDegreesFloat(float f, Operation<Float> original) {
        return FastMath.wrapDegrees(f);
    }

    @WrapMethod(method = "wrapDegrees(D)D")
    private static double papi$wrapDegreesDouble(double d, Operation<Double> original) {
        return FastMath.wrapDegrees(d);
    }

    @WrapMethod(method = "rotLerp(FFF)F")
    private static float papi$rotLerpFloat(float delta, float start, float end, Operation<Float> original) {
        return FastMath.rotLerp(delta, start, end);
    }

    @WrapMethod(method = "rotLerp(DDD)D")
    private static double papi$rotLerpDouble(double delta, double start, double end, Operation<Double> original) {
        return FastMath.rotLerp(delta, start, end);
    }

    @WrapMethod(method = "rotLerpRad")
    private static float papi$rotLerpRad(float delta, float start, float end, Operation<Float> original) {
        return FastMath.rotLerpRad(delta, start, end);
    }

    @WrapMethod(method = "smoothstep")
    private static float papi$smoothstep(float t, Operation<Float> original) {
        return t * t * t * Math.fma(t, Math.fma(t, 6.0F, -15.0F), 10.0F);
    }

    @WrapMethod(method = "lerp(FFF)F")
    private static float papi$lerpFloat(float delta, float start, float end, Operation<Float> original) {
        return Math.fma(delta, end - start, start);
    }

    @WrapMethod(method = "lerp(DDD)D")
    private static double papi$lerpDouble(double delta, double start, double end, Operation<Double> original) {
        return Math.fma(delta, end - start, start);
    }

    @WrapMethod(method = "lerp2(FFFFFF)F")
    private static float papi$lerp2Float(float dx, float dy, float v00, float v10, float v01, float v11, Operation<Float> original) {
        float x0 = Math.fma(dx, v10 - v00, v00);
        float x1 = Math.fma(dx, v11 - v01, v01);
        return Math.fma(dy, x1 - x0, x0);
    }

    @WrapMethod(method = "lerp2(DDDDDD)D")
    private static double papi$lerp2Double(double dx, double dy, double v00, double v10, double v01, double v11, Operation<Double> original) {
        double x0 = Math.fma(dx, v10 - v00, v00);
        double x1 = Math.fma(dx, v11 - v01, v01);
        return Math.fma(dy, x1 - x0, x0);
    }

    @WrapMethod(method = "lerp3(FFFFFFFFFFF)F")
    private static float papi$lerp3Float(
        float dx,
        float dy,
        float dz,
        float v000,
        float v100,
        float v010,
        float v110,
        float v001,
        float v101,
        float v011,
        float v111,
        Operation<Float> original
    ) {
        float x00 = Math.fma(dx, v100 - v000, v000);
        float x10 = Math.fma(dx, v110 - v010, v010);
        float x01 = Math.fma(dx, v101 - v001, v001);
        float x11 = Math.fma(dx, v111 - v011, v011);
        float y0 = Math.fma(dy, x10 - x00, x00);
        float y1 = Math.fma(dy, x11 - x01, x01);
        return Math.fma(dz, y1 - y0, y0);
    }

    @WrapMethod(method = "lerp3(DDDDDDDDDDD)D")
    private static double papi$lerp3Double(
        double dx,
        double dy,
        double dz,
        double v000,
        double v100,
        double v010,
        double v110,
        double v001,
        double v101,
        double v011,
        double v111,
        Operation<Double> original
    ) {
        double x00 = Math.fma(dx, v100 - v000, v000);
        double x10 = Math.fma(dx, v110 - v010, v010);
        double x01 = Math.fma(dx, v101 - v001, v001);
        double x11 = Math.fma(dx, v111 - v011, v011);
        double y0 = Math.fma(dy, x10 - x00, x00);
        double y1 = Math.fma(dy, x11 - x01, x01);
        return Math.fma(dz, y1 - y0, y0);
    }

    @WrapMethod(method = "catmullrom")
    private static float papi$catmullrom(float delta, float p0, float p1, float p2, float p3, Operation<Float> original) {
        float c3 = 3.0F * (p1 - p2) - p0 + p3;
        float c2 = 2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3;
        float c1 = p2 - p0;
        float c0 = 2.0F * p1;
        return 0.5F * Math.fma(delta, Math.fma(delta, Math.fma(delta, c3, c2), c1), c0);
    }
}
