package net.ashpapi.papioptimization.mixin.lighting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optimizes neighbor section notification during setStoredLevel.
 * 67% of blocks in a 16x16x16 chunk section are internal (coordinates 1..14).
 * For internal blocks, SectionPos.aroundAndAtBlockPos can only ever produce the current section node.
 * This fast-path avoids the 8-way section boundary evaluation and redundant iterations.
 */
@Mixin(value = LayerLightSectionStorage.class, priority = 1500)
public abstract class LayerLightSectionStorageMixin {

    @WrapOperation(
        method = "setStoredLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;aroundAndAtBlockPos(JLit/unimi/dsi/fastutil/longs/LongConsumer;)V"
        )
    )
    private void papi$fastAroundAndAtBlockPos(long blockNode, LongConsumer consumer, Operation<Void> original) {
        int x = BlockPos.getX(blockNode);
        int y = BlockPos.getY(blockNode);
        int z = BlockPos.getZ(blockNode);

        // Fast-path: strictly interior to section (coordinates 1..14 in X, Y, Z)
        if ((x & 15) != 0 && (x & 15) != 15 &&
            (y & 15) != 0 && (y & 15) != 15 &&
            (z & 15) != 0 && (z & 15) != 15) {
            consumer.accept(SectionPos.blockToSection(blockNode));
        } else {
            original.call(blockNode, consumer);
        }
    }
}
