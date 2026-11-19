package net.ashpapi.papioptimization.physics;

import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface PapiVoxelShapeExtension {
    @Nullable AABB papi$getSingleBox();
    boolean papi$isSingleBox();
    void papi$setSingleBoxState(@Nullable AABB box, byte state);
}
