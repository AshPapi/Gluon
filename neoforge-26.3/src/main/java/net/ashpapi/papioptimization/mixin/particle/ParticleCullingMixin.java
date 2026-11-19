package net.ashpapi.papioptimization.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.math.FastMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ShriekParticle;
import net.minecraft.client.particle.VibrationSignalParticle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Particle.class, priority = 1500)
public abstract class ParticleCullingMixin {

    @Shadow protected double xo;
    @Shadow protected double yo;
    @Shadow protected double zo;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected double xd;
    @Shadow protected double yd;
    @Shadow protected double zd;
    @Shadow protected int age;
    @Shadow protected int lifetime;
    @Shadow protected float gravity;
    @Shadow protected float friction;

    @Shadow public abstract void remove();
    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract void setBoundingBox(AABB bb);
    @Shadow protected abstract void setLocationFromBoundingbox();

    @Unique
    private boolean papi$isCriticalGameplayParticle() {
        Object self = this;
        return self instanceof NoRenderParticle
                || self instanceof VibrationSignalParticle
                || self instanceof ShriekParticle;
    }

    @WrapOperation(
        method = "move(DDD)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/particle/Particle;hasPhysics:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean papi$skipDistantParticleCollision(Particle self, Operation<Boolean> original) {
        if (!original.call(self)) {
            return false;
        }
        if (papi$isCriticalGameplayParticle()) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.gameRenderer == null) {
            return true;
        }
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double dx = this.x - camPos.x;
        double dy = this.y - camPos.y;
        double dz = this.z - camPos.z;
        return FastMath.isWithinDistance(dx, dy, dz, PapiConfig.particleLodDistance());
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$cullDistantParticlePhysics(CallbackInfo ci) {
        if (papi$isCriticalGameplayParticle()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.gameRenderer == null) {
            return;
        }
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double dx = this.x - camPos.x;
        double dy = this.y - camPos.y;
        double dz = this.z - camPos.z;
        double tickLodDist = Math.max(48.0, PapiConfig.particleLodDistance() + 18.0);

        if (!FastMath.isWithinDistance(dx, dy, dz, tickLodDist)) {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;

            if (this.age++ >= this.lifetime) {
                this.remove();
            } else {
                this.yd -= 0.04D * (double) this.gravity;
                this.setBoundingBox(this.getBoundingBox().move(this.xd, this.yd, this.zd));
                this.setLocationFromBoundingbox();
                double f = (double) this.friction;
                this.xd *= f;
                this.yd *= f;
                this.zd *= f;
            }
            ci.cancel();
        }
    }
}
