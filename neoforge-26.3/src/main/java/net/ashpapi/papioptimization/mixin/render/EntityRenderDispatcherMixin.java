package net.ashpapi.papioptimization.mixin.render;

import java.util.Map;
import net.ashpapi.papioptimization.client.EntityTypeRendererCache;
import net.ashpapi.papioptimization.client.PapiEntityCullingBox;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.client.occlusion.PapiSkeletalLod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
public abstract class EntityRenderDispatcherMixin {

    @Shadow private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;
    @Shadow public Camera camera;
    @Shadow public abstract <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T entity);

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void papi$populateEntityTypeRendererCache(ResourceManager resourceManager, CallbackInfo ci) {
        for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            ((EntityTypeRendererCache) type).papi$setRenderer(null);
        }
        if (this.renderers != null) {
            for (Map.Entry<EntityType<?>, EntityRenderer<?, ?>> entry : this.renderers.entrySet()) {
                ((EntityTypeRendererCache) entry.getKey()).papi$setRenderer(entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void papi$fastGetEntityRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (entity instanceof net.minecraft.world.entity.Avatar) {
            return;
        }
        EntityRenderer<?, ?> cached = ((EntityTypeRendererCache) entity.getType()).papi$getRenderer();
        if (cached != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) cached);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void papi$fastGetStateRenderer(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        if (state.entityType != null
                && state.entityType != net.minecraft.world.entity.EntityTypes.PLAYER
                && !(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState)) {
            EntityRenderer<?, ?> cached = ((EntityTypeRendererCache) state.entityType).papi$getRenderer();
            if (cached != null) {
                cir.setReturnValue((EntityRenderer<?, ? super S>) cached);
            }
        }
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void papi$occlusionCullEntity(
            E entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            float partialTick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()
                || this.camera == null
                || PapiOcclusionEngine.isShadowFrustum(frustum)
                || !(entity.level() instanceof ClientLevel cl)) {
            return;
        }
        Vec3 mainCamPos = this.camera.position();
        double dcx = camX - mainCamPos.x;
        double dcy = camY - mainCamPos.y;
        double dcz = camZ - mainCamPos.z;
        if (dcx * dcx + dcy * dcy + dcz * dcz > 1.0E-4) {
            return;
        }
        if (entity instanceof net.minecraft.world.entity.player.Player
                || entity == Minecraft.getInstance().getCameraEntity()
                || entity.isCurrentlyGlowing()
                || entity.hasCustomName()
                || entity.isVehicle()
                || entity.isPassenger()
                || entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Warden
                || (entity instanceof Leashable leashable && leashable.isLeashed())) {
            return;
        }
        EntityRenderer<? super E, ?> renderer = this.getRenderer(entity);
        AABB cullBox = (renderer instanceof PapiEntityCullingBox boxProvider)
                ? boxProvider.papi$getCullingBox(entity, partialTick)
                : entity.getBoundingBox();
        if (PapiOcclusionEngine.isBoxOccluded(cl, mainCamPos, cullBox, PapiOcclusionEngine.TARGET_ENTITY, entity.getId(), PapiSkeletalLod.ENTITY_CULL_MIN_DIST_SQR)) {
            cir.setReturnValue(false);
        }
    }
}
