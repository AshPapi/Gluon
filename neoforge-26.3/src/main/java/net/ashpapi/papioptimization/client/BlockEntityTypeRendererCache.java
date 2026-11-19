package net.ashpapi.papioptimization.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

public interface BlockEntityTypeRendererCache {
    BlockEntityRenderer<?, ?> papi$getRenderer();
    void papi$setRenderer(BlockEntityRenderer<?, ?> renderer);
    boolean papi$isRendererInitialized();
    void papi$resetRendererCache();
}
