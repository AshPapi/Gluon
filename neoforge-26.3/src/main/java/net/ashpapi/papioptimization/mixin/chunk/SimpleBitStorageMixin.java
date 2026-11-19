package net.ashpapi.papioptimization.mixin.chunk;

import net.ashpapi.papioptimization.access.FastBitStorageCounts;
import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntConsumer;

@Mixin(value = SimpleBitStorage.class, priority = 1500)
public abstract class SimpleBitStorageMixin implements FastBitStorageCounts {

    @Shadow @Final private long[] data;
    @Shadow @Final private int bits;
    @Shadow @Final private long mask;
    @Shadow @Final private int size;
    @Shadow @Final private int valuesPerLong;

    @Inject(method = "getAll", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetAll(IntConsumer output, CallbackInfo ci) {
        long[] words = this.data;
        int len = words.length;
        if (len == 0) {
            ci.cancel();
            return;
        }
        int valsPerLong = this.valuesPerLong;
        int b = this.bits;
        long m = this.mask;
        int limit = len - 1;
        for (int i = 0; i < limit; i++) {
            long word = words[i];
            for (int j = 0; j < valsPerLong; j++) {
                output.accept((int) (word & m));
                word >>>= b;
            }
        }
        int rem = this.size - limit * valsPerLong;
        long lastWord = words[limit];
        for (int j = 0; j < rem; j++) {
            output.accept((int) (lastWord & m));
            lastWord >>>= b;
        }
        ci.cancel();
    }

    @Override
    public void papi$countInto(int[] counts) {
        long[] words = this.data;
        int len = words.length;
        if (len == 0) return;
        int valsPerLong = this.valuesPerLong;
        int b = this.bits;
        long m = this.mask;
        int limit = len - 1;
        for (int i = 0; i < limit; i++) {
            long word = words[i];
            for (int j = 0; j < valsPerLong; j++) {
                counts[(int) (word & m)]++;
                word >>>= b;
            }
        }
        int rem = this.size - limit * valsPerLong;
        long lastWord = words[limit];
        for (int j = 0; j < rem; j++) {
            counts[(int) (lastWord & m)]++;
            lastWord >>>= b;
        }
    }
}
