package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.BlockEntityTypeRendererCache;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
@Mixin(value = BlockEntityType.class, priority = 1500)
public abstract class BlockEntityTypeMixin implements BlockEntityTypeRendererCache {

    @Unique
    private BlockEntityRenderer<?, ?> papi$cachedRenderer;

    @Unique
    private boolean papi$rendererInitialized = false;

    @Override
    public BlockEntityRenderer<?, ?> papi$getRenderer() {
        return this.papi$cachedRenderer;
    }

    @Override
    public void papi$setRenderer(BlockEntityRenderer<?, ?> renderer) {
        this.papi$cachedRenderer = renderer;
        this.papi$rendererInitialized = true;
    }

    @Override
    public boolean papi$isRendererInitialized() {
        return this.papi$rendererInitialized;
    }

    @Override
    public void papi$resetRendererCache() {
        this.papi$cachedRenderer = null;
        this.papi$rendererInitialized = false;
    }
}
