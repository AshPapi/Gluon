package net.ashpapi.papioptimization.mixin.chunk;

import java.util.IdentityHashMap;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiChunkMapTrackingState;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkMap.class, priority = 1500)
public abstract class ChunkMapTrackingStateMixin implements PapiChunkMapTrackingState {

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private int getPlayerViewDistance(ServerPlayer player) {
        throw new AssertionError();
    }

    @Unique
    private int papi$topologyEpoch = 1;

    @Unique
    private double papi$cumulativeMaxMove = 0.0;

    @Unique
    private final IdentityHashMap<ServerPlayer, double[]> papi$playerPosSnapshot = new IdentityHashMap<>();

    @Unique
    private int papi$lastPlayerCount = -1;

    @Unique
    private void papi$recordPlayerMove(ServerPlayer p) {
        double px = p.getX();
        double pz = p.getZ();
        int vd = this.getPlayerViewDistance(p);
        double[] snap = this.papi$playerPosSnapshot.get(p);
        if (snap == null) {
            this.papi$playerPosSnapshot.put(p, new double[]{px, pz, vd});
            this.papi$topologyEpoch++;
            return;
        }
        if ((int) snap[2] != vd) {
            this.papi$topologyEpoch++;
        }
        double dx = px - snap[0];
        double dz = pz - snap[1];
        double dSq = dx * dx + dz * dz;
        snap[0] = px;
        snap[1] = pz;
        snap[2] = vd;
        if (dSq > 0.0) {
            double step = Math.sqrt(dSq);
            this.papi$cumulativeMaxMove += step;
            if (step > 64.0) {
                this.papi$topologyEpoch++;
            }
        }
    }

    @Inject(method = "updatePlayerStatus", at = @At("HEAD"))
    private void papi$onUpdatePlayerStatus(ServerPlayer player, boolean track, CallbackInfo ci) {
        this.papi$topologyEpoch++;
        if (!track) {
            this.papi$playerPosSnapshot.remove(player);
        }
    }

    @Inject(method = "move(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void papi$onPlayerMove(ServerPlayer player, CallbackInfo ci) {
        papi$recordPlayerMove(player);
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void papi$updateTrackingEpochs(CallbackInfo ci) {
        List<ServerPlayer> players = this.level.players();
        int count = players.size();
        boolean topologyChanged = (count != this.papi$lastPlayerCount);
        this.papi$lastPlayerCount = count;
        if (count == 0) {
            if (!this.papi$playerPosSnapshot.isEmpty()) {
                this.papi$playerPosSnapshot.clear();
                this.papi$topologyEpoch++;
            }
            return;
        }
        double maxStepSq = 0.0;
        for (int i = 0; i < count; i++) {
            ServerPlayer p = players.get(i);
            double px = p.getX();
            double pz = p.getZ();
            int vd = this.getPlayerViewDistance(p);
            double[] snap = this.papi$playerPosSnapshot.get(p);
            if (snap == null) {
                snap = new double[]{px, pz, vd};
                this.papi$playerPosSnapshot.put(p, snap);
                topologyChanged = true;
            } else {
                if ((int) snap[2] != vd) {
                    topologyChanged = true;
                }
                double dx = px - snap[0];
                double dz = pz - snap[1];
                double dSq = dx * dx + dz * dz;
                if (dSq > maxStepSq) {
                    maxStepSq = dSq;
                }
                snap[0] = px;
                snap[1] = pz;
                snap[2] = vd;
            }
        }
        if (this.papi$playerPosSnapshot.size() > count) {
            this.papi$playerPosSnapshot.keySet().removeIf(p -> p.isRemoved() || p.level() != this.level);
            topologyChanged = true;
        }
        if (topologyChanged) {
            this.papi$topologyEpoch++;
        }
        if (maxStepSq > 0.0) {
            double step = Math.sqrt(maxStepSq);
            this.papi$cumulativeMaxMove += step;
            if (step > 64.0) {
                this.papi$topologyEpoch++;
            }
        }
    }

    @Override
    public int papi$getTopologyEpoch() {
        return this.papi$topologyEpoch;
    }

    @Override
    public double papi$getCumulativeMaxMove() {
        return this.papi$cumulativeMaxMove;
    }

    @Override
    public List<ServerPlayer> papi$getAllLevelPlayers() {
        return this.level.players();
    }
}
