package net.ashpapi.papioptimization.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = QuadParticleGroup.class, priority = 1500)
public abstract class QuadParticleGroupCullingMixin {

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"
        )
    )
    private boolean papi$cullOccludedParticle(
            Frustum frustum,
            double x,
            double y,
            double z,
            Operation<Boolean> original,
            @Local(argsOnly = true) Camera camera
    ) {
        if (!original.call(frustum, x, y, z)) {
            if (!PapiOcclusionEngine.isShadowFrustum(frustum) && !PapiOcclusionEngine.isShadowPass()) {
                PapiOcclusionEngine.recordFrustumCulledParticle();
            }
            return false;
        }
        if (camera == null || PapiOcclusionEngine.isShadowFrustum(frustum) || PapiOcclusionEngine.isShadowPass()) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc != null ? mc.level : null;
        if (level == null) {
            return true;
        }
        return !PapiOcclusionEngine.isParticleOccluded(level, camera.position(), x, y, z);
    }
}
