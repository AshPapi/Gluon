package net.ashpapi.papioptimization.client.occlusion;

import net.minecraft.world.phys.Vec3;

public final class PapiSkeletalLod {

    public static final double ENTITY_CULL_MIN_DIST_SQR = 64.0;
    public static final double BLOCK_ENTITY_CULL_MIN_DIST_SQR = 64.0;
    public static final double LAYER_LOD_DIST_SQR = 1936.0;

    private PapiSkeletalLod() {}

    public static boolean isBeyondLayerLod(Vec3 camPos, double ex, double ey, double ez) {
        double dx = ex - camPos.x;
        double dy = ey - camPos.y;
        double dz = ez - camPos.z;
        return dx * dx + dy * dy + dz * dz > LAYER_LOD_DIST_SQR;
    }
}
