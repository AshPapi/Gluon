package net.ashpapi.papioptimization.mixin.render;

import java.util.Map;
import net.ashpapi.papioptimization.client.BlockEntityTypeRendererCache;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.client.occlusion.PapiSkeletalLod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityRenderDispatcher.class, priority = 1500)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow private Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> renderers;
    @Shadow private Vec3 cameraPos;
    @Shadow public abstract <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable BlockEntityRenderer<E, S> getRenderer(E blockEntity);

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void papi$populateBlockEntityTypeRendererCache(ResourceManager resourceManager, CallbackInfo ci) {
        PapiClientCaches.invalidateAll();
        for (BlockEntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            ((BlockEntityTypeRendererCache) type).papi$resetRendererCache();
        }
        if (this.renderers != null) {
            for (Map.Entry<BlockEntityType<?>, BlockEntityRenderer<?, ?>> entry : this.renderers.entrySet()) {
                ((BlockEntityTypeRendererCache) entry.getKey()).papi$setRenderer(entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$fastGetBlockEntityRenderer(
            E blockEntity, CallbackInfoReturnable<@Nullable BlockEntityRenderer<E, S>> cir) {
        BlockEntityTypeRendererCache cache = (BlockEntityTypeRendererCache) blockEntity.getType();
        if (cache.papi$isRendererInitialized()) {
            cir.setReturnValue((BlockEntityRenderer<E, S>) cache.papi$getRenderer());
            return;
        }
        BlockEntityRenderer<?, ?> r = this.renderers.get(blockEntity.getType());
        cache.papi$setRenderer(r);
        cir.setReturnValue((BlockEntityRenderer<E, S>) r);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;)Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$fastGetStateRenderer(
            S state, CallbackInfoReturnable<@Nullable BlockEntityRenderer<E, S>> cir) {
        if (state.blockEntityType != null) {
            BlockEntityTypeRendererCache cache = (BlockEntityTypeRendererCache) state.blockEntityType;
            if (cache.papi$isRendererInitialized()) {
                cir.setReturnValue((BlockEntityRenderer<E, S>) cache.papi$getRenderer());
                return;
            }
            BlockEntityRenderer<?, ?> r = this.renderers.get(state.blockEntityType);
            cache.papi$setRenderer(r);
            cir.setReturnValue((BlockEntityRenderer<E, S>) r);
        }
    }

    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;createRenderState()Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"
        ),
        cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$occlusionCullBlockEntity(
            E blockEntity,
            float partialTick,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
            boolean renderOffScreen,
            @Nullable Frustum frustum,
            CallbackInfoReturnable<@Nullable S> cir
    ) {
        if (renderOffScreen
                || PapiOcclusionEngine.isShadowFrustum(frustum)
                || !(blockEntity.getLevel() instanceof ClientLevel cl)) {
            return;
        }
        if (blockEntity instanceof BeaconBlockEntity
                || blockEntity instanceof StructureBlockEntity
                || blockEntity instanceof JigsawBlockEntity
                || blockEntity instanceof TheEndPortalBlockEntity
                || blockEntity instanceof TheEndGatewayBlockEntity) {
            return;
        }
        BlockEntityRenderer<E, S> renderer = this.getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
        if (camera == null || this.cameraPos == null) {
            return;
        }
        Vec3 mainCamPos = camera.position();
        if (this.cameraPos.distanceToSqr(mainCamPos) > 1.0E-4) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        AABB box = renderer.getRenderBoundingBox(blockEntity);
        if (PapiOcclusionEngine.isBoxOccluded(cl, mainCamPos, box, PapiOcclusionEngine.TARGET_BLOCK_ENTITY, pos.asLong(), PapiSkeletalLod.BLOCK_ENTITY_CULL_MIN_DIST_SQR)) {
            cir.setReturnValue(null);
        }
    }
}
