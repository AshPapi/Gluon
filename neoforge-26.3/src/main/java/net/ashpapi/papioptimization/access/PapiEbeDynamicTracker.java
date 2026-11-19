package net.ashpapi.papioptimization.access;

public interface PapiEbeDynamicTracker {

    boolean papi$wasEbeDynamic();

    void papi$setEbeDynamic(boolean dynamic);

    long papi$getEbeCloseGraceUntil();

    void papi$setEbeCloseGraceUntil(long gameTime);

    boolean papi$isEbeBaked();

    void papi$setEbeBaked(boolean baked);
}
