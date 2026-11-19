package net.ashpapi.papioptimization.mixin.physics;

import net.minecraft.world.phys.shapes.CubePointRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CubePointRange.class, priority = 1500)
public abstract class CubePointRangeMixin {

    @Unique
    private double[] papi$points;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$precomputePoints(int parts, CallbackInfo ci) {
        if (parts <= 64) {
            double[] pts = new double[parts + 1];
            double div = (double) parts;
            for (int i = 0; i <= parts; i++) {
                pts[i] = (double) i / div;
            }
            this.papi$points = pts;
        }
    }

    @Inject(method = "getDouble", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetDouble(int index, CallbackInfoReturnable<Double> cir) {
        double[] pts = this.papi$points;
        if (pts != null && index >= 0 && index < pts.length) {
            cir.setReturnValue(pts[index]);
        }
    }
}
