package net.ashpapi.papioptimization.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.border.WorldBorder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldBorder.class, priority = 1500)
public abstract class WorldBorderFastMixin {

    @Unique
    private volatile double @Nullable [] papi$cachedBounds;

    @Unique
    private void papi$invalidateBounds() {
        this.papi$cachedBounds = null;
    }

    @Inject(method = "setCenter", at = @At("TAIL"))
    private void papi$onSetCenter(double x, double z, CallbackInfo ci) {
        this.papi$invalidateBounds();
    }

    @Inject(method = "setSize", at = @At("TAIL"))
    private void papi$onSetSize(double size, CallbackInfo ci) {
        this.papi$invalidateBounds();
    }

    @Inject(method = "lerpSizeBetween", at = @At("TAIL"))
    private void papi$onLerpSize(double from, double to, long time, long gameTime, CallbackInfo ci) {
        this.papi$invalidateBounds();
    }

    @Inject(method = "setAbsoluteMaxSize", at = @At("TAIL"))
    private void papi$onSetAbsoluteMaxSize(int maxSize, CallbackInfo ci) {
        this.papi$invalidateBounds();
    }

    @Inject(method = "applyInitialSettings", at = @At("TAIL"))
    private void papi$onApplyInitialSettings(long gameTime, CallbackInfo ci) {
        this.papi$invalidateBounds();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void papi$onTick(CallbackInfo ci) {
        WorldBorder self = (WorldBorder) (Object) this;
        if (self.getLerpTime() > 0L) {
            this.papi$invalidateBounds();
        }
    }

    @Unique
    private double[] papi$computeBounds(WorldBorder self) {
        double[] snap = new double[]{
            self.getMinX(0.0F),
            self.getMaxX(0.0F),
            self.getMinZ(0.0F),
            self.getMaxZ(0.0F)
        };
        if (self.getLerpTime() <= 0L) {
            this.papi$cachedBounds = snap;
        }
        return snap;
    }

    @WrapMethod(method = "getMinX()D")
    private double papi$fastGetMinX(Operation<Double> original) {
        WorldBorder self = (WorldBorder) (Object) this;
        if (self.getLerpTime() > 0L) {
            this.papi$cachedBounds = null;
            return original.call();
        }
        double[] snap = this.papi$cachedBounds;
        if (snap != null) {
            return snap[0];
        }
        return papi$computeBounds(self)[0];
    }

    @WrapMethod(method = "getMaxX()D")
    private double papi$fastGetMaxX(Operation<Double> original) {
        WorldBorder self = (WorldBorder) (Object) this;
        if (self.getLerpTime() > 0L) {
            this.papi$cachedBounds = null;
            return original.call();
        }
        double[] snap = this.papi$cachedBounds;
        if (snap != null) {
            return snap[1];
        }
        return papi$computeBounds(self)[1];
    }

    @WrapMethod(method = "getMinZ()D")
    private double papi$fastGetMinZ(Operation<Double> original) {
        WorldBorder self = (WorldBorder) (Object) this;
        if (self.getLerpTime() > 0L) {
            this.papi$cachedBounds = null;
            return original.call();
        }
        double[] snap = this.papi$cachedBounds;
        if (snap != null) {
            return snap[2];
        }
        return papi$computeBounds(self)[2];
    }

    @WrapMethod(method = "getMaxZ()D")
    private double papi$fastGetMaxZ(Operation<Double> original) {
        WorldBorder self = (WorldBorder) (Object) this;
        if (self.getLerpTime() > 0L) {
            this.papi$cachedBounds = null;
            return original.call();
        }
        double[] snap = this.papi$cachedBounds;
        if (snap != null) {
            return snap[3];
        }
        return papi$computeBounds(self)[3];
    }
}
