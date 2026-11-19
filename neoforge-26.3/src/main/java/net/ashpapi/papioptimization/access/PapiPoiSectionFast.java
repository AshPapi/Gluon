package net.ashpapi.papioptimization.access;

import net.minecraft.world.entity.ai.village.poi.PoiManager;

public interface PapiPoiSectionFast {

    boolean papi$isEmpty();

    boolean papi$mayMatchOccupancy(PoiManager.Occupancy occupancy);

    void papi$markOccupancyDirty();
}
