package net.ashpapi.papioptimization.mixin.map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.map.FastMapColorCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Accelerates map texture uploads by replacing branch-heavy MapColor decoding
 * with a precomputed O(1) 256-entry ARGB lookup table.
 */
@Mixin(targets = "net.minecraft.client.resources.MapTextureManager$MapInstance", priority = 1500)
public abstract class MapInstanceTextureMixin {

    @WrapOperation(
        method = "updateTextureIfNeeded",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/MapColor;getColorFromPackedId(I)I"
        )
    )
    private int papi$fastColorLookup(int packedId, Operation<Integer> original) {
        if ((packedId & ~0xFF) == 0) {
            return FastMapColorCache.getArgbColor(packedId);
        }
        return original.call(packedId);
    }
}
