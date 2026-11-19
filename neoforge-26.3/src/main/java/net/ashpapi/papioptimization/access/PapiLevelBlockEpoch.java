package net.ashpapi.papioptimization.access;

public interface PapiLevelBlockEpoch {

    long papi$getBlockEpoch(long posLong);

    void papi$bumpBlockEpoch(long posLong);
}
