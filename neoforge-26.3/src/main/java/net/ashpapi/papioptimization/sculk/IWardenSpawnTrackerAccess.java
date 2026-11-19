package net.ashpapi.papioptimization.sculk;

import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;

public interface IWardenSpawnTrackerAccess {
    boolean papi$onCooldown();
    void papi$increaseWarningLevel();
    void papi$copyData(WardenSpawnTracker copyFrom);
}
