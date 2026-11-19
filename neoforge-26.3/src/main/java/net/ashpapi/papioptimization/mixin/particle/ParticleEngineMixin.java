package net.ashpapi.papioptimization.mixin.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = ParticleEngine.class, priority = 1500)
public abstract class ParticleEngineMixin {

    @Shadow @Final private Map<ParticleRenderType, ParticleGroup<?>> particles;

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void papi$skipEmptyParticleExtract(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
        if (this.particles.isEmpty()) {
            ci.cancel();
        }
    }
}
