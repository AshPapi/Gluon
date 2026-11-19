package net.ashpapi.papioptimization.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public interface PapiEntityCullingBox {
    AABB papi$getCullingBox(Entity entity, float partialTick);
}
