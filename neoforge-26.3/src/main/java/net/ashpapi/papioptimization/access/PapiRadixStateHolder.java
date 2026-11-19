package net.ashpapi.papioptimization.access;

import net.ashpapi.papioptimization.memory.PapiRadixTable;

public interface PapiRadixStateHolder<S> {
    void papi$attachRadix(PapiRadixTable<S> table, int stateIndex);

    boolean papi$hasRadix();
}
