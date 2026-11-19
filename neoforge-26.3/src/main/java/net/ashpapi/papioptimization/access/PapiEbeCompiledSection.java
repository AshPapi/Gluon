package net.ashpapi.papioptimization.access;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PapiEbeCompiledSection {

    void papi$addEbeBakedTracker(PapiEbeDynamicTracker tracker);

    void papi$addEbeUnbakedTracker(PapiEbeDynamicTracker tracker);

    @Nullable List<PapiEbeDynamicTracker> papi$getEbeBakedTrackers();

    @Nullable List<PapiEbeDynamicTracker> papi$getEbeUnbakedTrackers();

    void papi$copyEbeTrackersFrom(PapiEbeCompiledSection source);
}
