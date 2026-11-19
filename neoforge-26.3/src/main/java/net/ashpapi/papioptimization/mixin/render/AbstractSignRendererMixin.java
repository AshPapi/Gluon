package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractSignRenderer.class, priority = 1500)
public abstract class AbstractSignRendererMixin {

    @Unique
    private static boolean papi$isFaceVisible(Transformation transform, BlockPos pos, Vec3 cameraPos) {
        Matrix4fc m = transform.getMatrix();
        float dx = (float) (cameraPos.x - ((double) pos.getX() + (double) m.m30()));
        float dy = (float) (cameraPos.y - ((double) pos.getY() + (double) m.m31()));
        float dz = (float) (cameraPos.z - ((double) pos.getZ() + (double) m.m32()));
        return Math.fma(m.m20(), dx, Math.fma(m.m21(), dy, m.m22() * dz)) > 0.0F;
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At("RETURN")
    )
    private void papi$cullSignText(
        SignBlockEntity sign,
        SignRenderState state,
        float partialTick,
        Vec3 cameraPos,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
        CallbackInfo ci
    ) {
        if (state.frontText != null && !state.frontText.hasMessage(false)) {
            state.frontText = null;
        }
        if (state.backText != null && !state.backText.hasMessage(false)) {
            state.backText = null;
        }
        if ((state.frontText == null && state.backText == null) || cameraPos == null || PapiOcclusionEngine.isShadowPass()) {
            return;
        }
        BlockPos pos = sign.getBlockPos();
        double cx = (double) pos.getX() + 0.5;
        double cy = (double) pos.getY() + 0.5;
        double cz = (double) pos.getZ() + 0.5;
        double dx = cameraPos.x - cx;
        double dy = cameraPos.y - cy;
        double dz = cameraPos.z - cz;

        Minecraft mc = Minecraft.getInstance();
        boolean scoping = mc.player != null && mc.options.getCameraType().isFirstPerson() && mc.player.isScoping();
        if (!scoping) {
            int fov = mc.options.fov().get();
            double limitSqr = (fov > 0 && fov < 70) ? 4096.0 * (4900.0 / (double) (fov * fov)) : 4096.0;
            if (dx * dx + dy * dy + dz * dz > limitSqr) {
                state.frontText = null;
                state.backText = null;
                return;
            }
        }

        BlockState blockState = sign.getBlockState();
        if (blockState.getBlock() instanceof SignBlock signBlock) {
            float rad = signBlock.getYRotationDegrees(blockState) * ((float) Math.PI / 180.0F);
            double nx = -Mth.sin(rad);
            double nz = Mth.cos(rad);
            if (signBlock instanceof WallSignBlock) {
                dx += nx * 0.4375;
                dz += nz * 0.4375;
            }
            double dot = dx * nx + dz * nz;
            if (dot < -0.1) {
                state.frontText = null;
            } else if (dot > 0.1) {
                state.backText = null;
            }
        }
    }

    @WrapOperation(
        method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/AbstractSignRenderer;submitSignText(Lnet/minecraft/client/renderer/blockentity/state/SignRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/level/block/entity/SignText;)V"
        )
    )
    private void papi$cullSignTextFaceOnSubmit(
        AbstractSignRenderer<?> instance,
        SignRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        SignText signText,
        Operation<Void> original,
        @Local(argsOnly = true) CameraRenderState cameraRenderState
    ) {
        if (signText == null || !signText.hasMessage(false)) {
            return;
        }
        if (!PapiOcclusionEngine.isShadowPass()
                && state.transformations != null
                && state.transformations != SignRenderState.SignTransformations.IDENTITY
                && state.blockPos != null
                && cameraRenderState != null
                && cameraRenderState.pos != null) {
            Transformation transform = (signText == state.frontText)
                    ? state.transformations.frontText()
                    : state.transformations.backText();
            if (transform != null && !papi$isFaceVisible(transform, state.blockPos, cameraRenderState.pos)) {
                return;
            }
        }
        original.call(instance, state, poseStack, submitNodeCollector, signText);
    }

    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
    private void papi$skipBlankSignText(SignRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, SignText signText, CallbackInfo ci) {
        if (signText == null || !signText.hasMessage(false)) {
            ci.cancel();
        }
    }
}
