package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockEntityRenderDispatcher.class, priority = 1500)
public abstract class BlockEntityRenderDispatcherEbeMixin {

    @Shadow
    private Vec3 cameraPos;

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void papi$clearEbeCacheOnReload(ResourceManager resourceManager, CallbackInfo ci) {
        PapiEbeRuntime.clearCache();
    }

    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$skipBakedStaticBlockEntity(
            E blockEntity,
            float partialTick,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
            boolean renderOffScreen,
            @Nullable Frustum frustum,
            CallbackInfoReturnable<@Nullable S> cir
    ) {
        if (!(blockEntity.getLevel() instanceof ClientLevel cl)) {
            return;
        }
        Vec3 cam = this.cameraPos;
        if (cam != null) {
            PapiEbeRuntime.updateFrameContext(cam.x, cam.y, cam.z, cl.getGameTime());
        }
        if (crumblingOverlay == null && blockEntity instanceof SignBlockEntity sign) {
            if (!sign.getText(SignTextSlot.FRONT).hasMessage(false) && !sign.getText(SignTextSlot.BACK).hasMessage(false)) {
                cir.setReturnValue(null);
                return;
            }
        }
        if (PapiEbeRuntime.shouldSkipDynamicRender(blockEntity, cl, crumblingOverlay != null)) {
            cir.setReturnValue(null);
        }
    }
}
