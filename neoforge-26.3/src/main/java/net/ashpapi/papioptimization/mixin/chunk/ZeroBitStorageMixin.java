package net.ashpapi.papioptimization.mixin.chunk;

import net.ashpapi.papioptimization.access.FastBitStorageCounts;
import net.minecraft.util.ZeroBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ZeroBitStorage.class, priority = 1500)
public abstract class ZeroBitStorageMixin implements FastBitStorageCounts {

    @Shadow @Final private int size;

    @Override
    public void papi$countInto(int[] counts) {
        if (counts.length > 0) {
            counts[0] += this.size;
        }
    }
}
