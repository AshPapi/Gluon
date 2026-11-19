package net.ashpapi.papioptimization.mixin.core;

import net.ashpapi.papioptimization.access.PapiLevelBlockEpoch;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Level.class, priority = 1500)
public abstract class LevelBlockEpochMixin implements PapiLevelBlockEpoch {

    @Unique
    private static final int PAPI$EPOCH_TABLE_SIZE = 4096;

    @Unique
    private static final int PAPI$EPOCH_TABLE_MASK = PAPI$EPOCH_TABLE_SIZE - 1;

    @Unique
    private final long[] papi$blockEpochs = new long[PAPI$EPOCH_TABLE_SIZE];

    @Unique
    private long papi$blockEpochSeq = 1L;

    @Unique
    private static int papi$epochSlot(long posLong) {
        long z = (posLong ^ (posLong >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return (int) (z ^ (z >>> 31)) & PAPI$EPOCH_TABLE_MASK;
    }

    @Override
    public long papi$getBlockEpoch(long posLong) {
        return this.papi$blockEpochs[papi$epochSlot(posLong)];
    }

    @Override
    public void papi$bumpBlockEpoch(long posLong) {
        long next = ++this.papi$blockEpochSeq;
        this.papi$blockEpochs[papi$epochSlot(posLong)] = next;
    }
}
