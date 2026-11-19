package net.ashpapi.papioptimization.access;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public interface PapiChunkMapTrackingState {

    int papi$getTopologyEpoch();

    double papi$getCumulativeMaxMove();

    List<ServerPlayer> papi$getAllLevelPlayers();
}
