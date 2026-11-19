package net.ashpapi.papioptimization.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = SingleQuadParticle.class, priority = 1500)
public abstract class SingleQuadParticleMixin extends Particle {

    @Shadow
    protected float roll;

    @Shadow
    protected float oRoll;

    @Shadow
    public abstract SingleQuadParticle.FacingCameraMode getFacingCameraMode();

    @Shadow
    protected abstract void extractRotatedQuad(
            QuadParticleRenderState state,
            Camera camera,
            Quaternionf rotation,
            float partialTick
    );

    @Unique
    private static final ThreadLocal<Quaternionf> PAPI$QUAT = ThreadLocal.withInitial(Quaternionf::new);

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @WrapMethod(method = "extract")
    private void papi$fastExtract(
            QuadParticleRenderState state,
            Camera camera,
            float partialTick,
            Operation<Void> original
    ) {
        Quaternionf q = PAPI$QUAT.get();
        SingleQuadParticle.FacingCameraMode mode = this.getFacingCameraMode();
        if (mode == SingleQuadParticle.FacingCameraMode.LOOKAT_XYZ) {
            q.set(camera.rotation());
        } else if (mode == SingleQuadParticle.FacingCameraMode.LOOKAT_Y) {
            Quaternionf cr = camera.rotation();
            q.set(0.0F, cr.y, 0.0F, cr.w);
        } else {
            q.identity();
            mode.setRotation(q, camera, partialTick);
        }
        if (this.roll != 0.0F) {
            q.rotateZ(Mth.lerp(partialTick, this.oRoll, this.roll));
        }
        this.extractRotatedQuad(state, camera, q, partialTick);
    }
}
