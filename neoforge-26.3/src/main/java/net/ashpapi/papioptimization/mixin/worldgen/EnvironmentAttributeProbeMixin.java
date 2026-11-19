package net.ashpapi.papioptimization.mixin.worldgen;

import java.lang.ref.WeakReference;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EnvironmentAttributeProbe.class, priority = 1500)
public abstract class EnvironmentAttributeProbeMixin {

    @Unique
    private WeakReference<Level> papi$cachedLevel = new WeakReference<>(null);

    @Unique
    private double papi$lastX = Double.NaN;

    @Unique
    private double papi$lastY = Double.NaN;

    @Unique
    private double papi$lastZ = Double.NaN;

    @Unique
    private long papi$lastSampleTick = Long.MIN_VALUE;

    @Inject(method = "reset", at = @At("RETURN"))
    private void papi$onReset(CallbackInfo ci) {
        this.papi$cachedLevel = new WeakReference<>(null);
        this.papi$lastX = Double.NaN;
        this.papi$lastSampleTick = Long.MIN_VALUE;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;clear()V"
            ),
            cancellable = true
    )
    private void papi$skipRedundantBiomeSampling(Level level, Vec3 position, CallbackInfo ci) {
        long gameTime = level.getGameTime();
        if (this.papi$cachedLevel.get() == level && (gameTime - this.papi$lastSampleTick) < 20L && gameTime >= this.papi$lastSampleTick) {
            double dx = position.x - this.papi$lastX;
            double dy = position.y - this.papi$lastY;
            double dz = position.z - this.papi$lastZ;
            if (dx * dx + dy * dy + dz * dz < 1.0E-4) {
                ci.cancel();
                return;
            }
        }
        this.papi$cachedLevel = new WeakReference<>(level);
        this.papi$lastX = position.x;
        this.papi$lastY = position.y;
        this.papi$lastZ = position.z;
        this.papi$lastSampleTick = gameTime;
    }
}
