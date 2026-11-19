package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.EntityTypeRendererCache;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
@Mixin(value = EntityType.class, priority = 1500)
public abstract class EntityTypeMixin implements EntityTypeRendererCache {

    @Unique
    private EntityRenderer<?, ?> papi$cachedRenderer;

    @Override
    public EntityRenderer<?, ?> papi$getRenderer() {
        return this.papi$cachedRenderer;
    }

    @Override
    public void papi$setRenderer(EntityRenderer<?, ?> renderer) {
        this.papi$cachedRenderer = renderer;
    }
}
