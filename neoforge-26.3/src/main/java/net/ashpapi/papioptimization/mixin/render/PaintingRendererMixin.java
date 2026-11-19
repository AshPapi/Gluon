package net.ashpapi.papioptimization.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ashpapi.papioptimization.access.PapiCullableDecorationState;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PaintingRenderer.class, priority = 1500)
public abstract class PaintingRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/painting/Painting;Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void papi$fastExtractPaintingState(Painting painting, PaintingRenderState state, float partialTick, CallbackInfo ci) {
        Direction direction = painting.getDirection();
        PaintingVariant variant = painting.getVariant().value();
        state.direction = direction;
        state.variant = variant;
        int width = variant.width();
        int height = variant.height();
        int total = width * height;
        if (state.lightCoordsPerBlock.length != total) {
            state.lightCoordsPerBlock = new int[total];
        }
        float halfW = (float) (-width) / 2.0F;
        float halfH = (float) (-height) / 2.0F;
        Level level = painting.level();
        int baseBlockX = painting.getBlockX();
        int baseBlockZ = painting.getBlockZ();
        double px = painting.getX();
        double py = painting.getY();
        double pz = painting.getZ();
        int backStepX = -direction.getStepX();
        int backStepZ = -direction.getStepZ();
        boolean allBackSolid = true;

        int mark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos pos = BlockPosPool.get();
            for (int row = 0; row < height; row++) {
                float offsetY = (float) row + halfH + 0.5F;
                int blockY = Mth.floor(py + (double) offsetY);
                int rowBase = row * width;
                for (int col = 0; col < width; col++) {
                    float offsetX = (float) col + halfW + 0.5F;
                    int blockX = baseBlockX;
                    int blockZ = baseBlockZ;
                    if (direction == Direction.NORTH) {
                        blockX = Mth.floor(px + (double) offsetX);
                    } else if (direction == Direction.WEST) {
                        blockZ = Mth.floor(pz - (double) offsetX);
                    } else if (direction == Direction.SOUTH) {
                        blockX = Mth.floor(px - (double) offsetX);
                    } else if (direction == Direction.EAST) {
                        blockZ = Mth.floor(pz + (double) offsetX);
                    }
                    pos.set(blockX, blockY, blockZ);
                    state.lightCoordsPerBlock[rowBase + col] = LightCoordsUtil.getLightCoords(level, pos);
                    if (allBackSolid) {
                        pos.set(blockX + backStepX, blockY, blockZ + backStepZ);
                        BlockState back = level.getBlockState(pos);
                        if (back.isAir() || !back.canOcclude() || !back.isSolidRender()) {
                            allBackSolid = false;
                        }
                    }
                }
            }
        } finally {
            BlockPosPool.reset(mark);
        }
        ((PapiCullableDecorationState) state).papi$setBackOccluded(allBackSolid);
        ci.cancel();
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$cullBackfacingPainting(
            PaintingRenderState state,
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
                + (cameraRenderState.pos.z - state.z) * (double) dir.getStepZ();
        if (dot < -0.08) {
            ci.cancel();
        }
    }
}
