package net.ashpapi.papioptimization.access;

public interface PapiCullableDecorationState {

    boolean papi$isBackOccluded();

    void papi$setBackOccluded(boolean occluded);
}
