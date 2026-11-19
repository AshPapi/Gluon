package net.ashpapi.papioptimization.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BeaconRenderer.class, priority = 1500)
public abstract class BeaconRendererMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BeaconRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void papi$skipEmptyBeaconSubmit(BeaconRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (state.sections.isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true)
    private static void papi$cullBackfacingInnerBeaconQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int color,
            int minY,
            int maxY,
            float x1,
            float z1,
            float x2,
            float z2,
            float u1,
            float u2,
            float v1,
            float v2,
            CallbackInfo ci
    ) {
        if (maxY <= minY || ((color >>> 24) & 0xFF) != 0xFF || PapiOcclusionEngine.isShadowPass()) {
            return;
        }
        Matrix4f m = pose.pose();
        float p0x = m.m00() * x1 + m.m10() * (float) maxY + m.m20() * z1 + m.m30();
        float p0y = m.m01() * x1 + m.m11() * (float) maxY + m.m21() * z1 + m.m31();
        float p0z = m.m02() * x1 + m.m12() * (float) maxY + m.m22() * z1 + m.m32();

        float dy = (float) (minY - maxY);
        float e1x = m.m10() * dy;
        float e1y = m.m11() * dy;
        float e1z = m.m12() * dy;

        float dx = x2 - x1;
        float dz = z2 - z1;
        float e2x = m.m00() * dx + m.m10() * dy + m.m20() * dz;
        float e2y = m.m01() * dx + m.m11() * dy + m.m21() * dz;
        float e2z = m.m02() * dx + m.m12() * dy + m.m22() * dz;

        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;

        float viewDot = -(nx * p0x + ny * p0y + nz * p0z);
        if (viewDot < -1.0E-3F) {
            ci.cancel();
        }
    }
}
