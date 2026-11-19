package net.ashpapi.papioptimization.mixin.chunk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.ashpapi.papioptimization.access.FastBitStorageCounts;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PalettedContainer.class, priority = 1500)
public abstract class PalettedContainerCountMixin<T> {

    @Unique
    private static final VarHandle PAPI$DATA_HANDLE = papi$resolveDataHandle();

    @Unique
    private static VarHandle papi$resolveDataHandle() {
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            return MethodHandles.privateLookupIn(PalettedContainer.class, MethodHandles.lookup())
                    .findVarHandle(PalettedContainer.class, "data", dataClass);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve PalettedContainer.data VarHandle", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void papi$count(PalettedContainer.CountConsumer<T> output, CallbackInfo ci) {
        PalettedContainerDataAccessor<T> accessor = (PalettedContainerDataAccessor<T>) PAPI$DATA_HANDLE.getVolatile(this);
        Palette<T> palette = accessor.papi$getPalette();
        BitStorage storage = accessor.papi$getStorage();

        int paletteSize = palette.getSize();
        if (paletteSize == 1) {
            output.accept(palette.valueFor(0), storage.getSize());
            ci.cancel();
            return;
        }

        int bits = storage.getBits();
        if (bits < 0 || bits > 12) {
            return;
        }
        int maxCapacity = Math.max(paletteSize, 1 << bits);
        if (maxCapacity > 4096) {
            return;
        }

        int[] counts = new int[maxCapacity];
        if (storage instanceof FastBitStorageCounts fastStorage) {
            fastStorage.papi$countInto(counts);
        } else {
            storage.getAll(id -> counts[id]++);
        }

        for (int id = 0; id < paletteSize; id++) {
            int count = counts[id];
            if (count > 0) {
                output.accept(palette.valueFor(id), count);
            }
        }
        ci.cancel();
    }
}
