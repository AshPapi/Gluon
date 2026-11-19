package net.ashpapi.papioptimization.mixin.math;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ARGB.class, priority = 1500)
public abstract class ARGBMixin {

    @Shadow @Final private static short[] SRGB_TO_LINEAR;
    @Shadow @Final private static byte[] LINEAR_TO_SRGB;

    @WrapMethod(method = "srgbLerp(FII)I")
    private static int papi$srgbLerp(float delta, int color1, int color2, Operation<Integer> original) {
        if (color1 == color2 || delta <= 0.0F) {
            return color1;
        }
        if (delta >= 1.0F) {
            return color2;
        }
        return ARGB.color(
                Mth.lerpInt(delta, ARGB.alpha(color1), ARGB.alpha(color2)),
                Mth.lerpInt(delta, ARGB.red(color1), ARGB.red(color2)),
                Mth.lerpInt(delta, ARGB.green(color1), ARGB.green(color2)),
                Mth.lerpInt(delta, ARGB.blue(color1), ARGB.blue(color2))
        );
    }

    @WrapMethod(method = "linearLerp")
    private static int papi$linearLerp(float delta, int color1, int color2, Operation<Integer> original) {
        if (color1 == color2 || delta <= 0.0F) {
            return color1;
        }
        if (delta >= 1.0F) {
            return color2;
        }
        return ARGB.color(
                Mth.lerpInt(delta, ARGB.alpha(color1), ARGB.alpha(color2)),
                LINEAR_TO_SRGB[Mth.lerpInt(delta, SRGB_TO_LINEAR[ARGB.red(color1)], SRGB_TO_LINEAR[ARGB.red(color2)])] & 255,
                LINEAR_TO_SRGB[Mth.lerpInt(delta, SRGB_TO_LINEAR[ARGB.green(color1)], SRGB_TO_LINEAR[ARGB.green(color2)])] & 255,
                LINEAR_TO_SRGB[Mth.lerpInt(delta, SRGB_TO_LINEAR[ARGB.blue(color1)], SRGB_TO_LINEAR[ARGB.blue(color2)])] & 255
        );
    }
}
