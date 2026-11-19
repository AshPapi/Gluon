package net.ashpapi.papioptimization.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ashpapi.papioptimization.access.PapiCullableDecorationState;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemFrameRenderer.class, priority = 1500)
public abstract class ItemFrameRendererMixin<T extends ItemFrame> {

    @Unique
    private static final Vec3[] PAPI$RENDER_OFFSETS = new Vec3[6];

    static {
        for (Direction dir : Direction.values()) {
            PAPI$RENDER_OFFSETS[dir.get3DDataValue()] = new Vec3(
                (double) ((float) dir.getStepX() * 0.3F),
                -0.25,
                (double) ((float) dir.getStepZ() * 0.3F)
            );
        }
    }

    @Inject(
        method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastGetRenderOffset(ItemFrameRenderState state, CallbackInfoReturnable<Vec3> cir) {
        if (state.direction != null) {
            cir.setReturnValue(PAPI$RENDER_OFFSETS[state.direction.get3DDataValue()]);
        }
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
        at = @At("RETURN")
    )
    private void papi$extractBackfaceOcclusion(T frame, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
        Direction dir = frame.getDirection();
        BlockPos supportPos = frame.getPos().relative(dir.getOpposite());
        BlockState support = frame.level().getBlockState(supportPos);
        boolean solidBack = !support.isAir() && support.canOcclude() && support.isSolidRender();
        ((PapiCullableDecorationState) state).papi$setBackOccluded(solidBack);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$cullBackfacingItemFrame(
            ItemFrameRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!((PapiCullableDecorationState) state).papi$isBackOccluded()
                || cameraRenderState == null
                || cameraRenderState.pos == null
                || state.direction == null
                || PapiOcclusionEngine.isShadowPass()) {
            return;
        }
        Direction dir = state.direction;
        double dot = (cameraRenderState.pos.x - state.x) * (double) dir.getStepX()
                + (cameraRenderState.pos.y - state.y) * (double) dir.getStepY()
                + (cameraRenderState.pos.z - state.z) * (double) dir.getStepZ();
        double threshold = (state.item == null || state.item.isEmpty()) ? -0.08 : -0.35;
        if (dot < threshold) {
            ci.cancel();
        }
    }
}
