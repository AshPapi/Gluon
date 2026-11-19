package net.ashpapi.papioptimization.pathfinding;

import net.minecraft.world.level.pathfinder.PathType;

public interface PapiBlockStatePathType {
    boolean papi$isCacheable();
    PathType papi$getCachedPathType(int epoch);
    void papi$setCachedPathType(PathType type, int epoch);
}
