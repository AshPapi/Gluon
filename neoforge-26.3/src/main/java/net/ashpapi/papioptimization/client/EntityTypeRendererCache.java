package net.ashpapi.papioptimization.client;

import net.minecraft.client.renderer.entity.EntityRenderer;

public interface EntityTypeRendererCache {
    EntityRenderer<?, ?> papi$getRenderer();
    void papi$setRenderer(EntityRenderer<?, ?> renderer);
}
