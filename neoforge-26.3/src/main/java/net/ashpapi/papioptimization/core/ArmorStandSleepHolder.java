package net.ashpapi.papioptimization.core;

public interface ArmorStandSleepHolder {
    int papi$getSleepTicks();
    void papi$setSleepTicks(int ticks);
    void papi$wakeUp();
    boolean papi$isSleeping();
    boolean papi$hasMovedSinceSleep(double x, double y, double z);
    void papi$recordSleepPos(double x, double y, double z);
}
