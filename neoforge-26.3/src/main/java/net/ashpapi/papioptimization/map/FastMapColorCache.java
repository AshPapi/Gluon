package net.ashpapi.papioptimization.map;

import net.minecraft.world.level.material.MapColor;

/**
 * Precalculated 256-entry ARGB lookup table for map texture uploads.
 * Eliminates repeated branch-heavy color calculation in MapTextureManager$MapInstance.
 */
public final class FastMapColorCache {
    private static final int[] PACKED_COLOR_CACHE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            PACKED_COLOR_CACHE[i] = MapColor.getColorFromPackedId(i);
        }
    }

    private FastMapColorCache() {}

    public static int getArgbColor(int packedId) {
        return PACKED_COLOR_CACHE[packedId & 0xFF];
    }
}
