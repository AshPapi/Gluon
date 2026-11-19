package net.ashpapi.papioptimization.mixin.worldgen;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 1500)
public abstract class MinecraftServerSpawnMixin {
    @Shadow @Final private static long PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
    @Shadow protected long nextTickTimeNanos;
    @Shadow public abstract LevelLoadListener getLevelLoadListener();
    @Shadow public abstract void updateMobSpawningFlags();
    @Shadow protected abstract void updateEffectiveRespawnData();
    @Shadow public abstract Iterable<ServerLevel> getAllLevels();
    @Shadow protected abstract void waitUntilNextTick();

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void papi$instantSpawnLoading(CallbackInfo ci) {
        ChunkLoadCounter counter = new ChunkLoadCounter();
        for (ServerLevel serverLevel : this.getAllLevels()) {
            counter.track(serverLevel, () -> {
                TicketStorage ticketStorage = serverLevel.getDataStorage().get(TicketStorage.TYPE);
                if (ticketStorage != null) {
                    ticketStorage.activateAllDeactivatedTickets();
                    ForcedChunkManager.activateAllDeactivatedTickets(serverLevel, ticketStorage);
                }
                LongSet forced = serverLevel.getForceLoadedChunks();
                if (!forced.isEmpty()) {
                    LongIterator it = forced.iterator();
                    while (it.hasNext()) {
                        long packed = it.nextLong();
                        serverLevel.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), true);
                    }
                }
            });
        }
        LevelLoadListener listener = this.getLevelLoadListener();
        int totalChunks = counter.totalChunks();
        if (listener != null) {
            listener.start(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, totalChunks);
        }
        while (counter.pendingChunks() > 0) {
            if (listener != null) {
                listener.update(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, counter.readyChunks(), totalChunks);
            }
            this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.waitUntilNextTick();
        }
        if (listener != null) {
            listener.finish(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS);
        }
        this.updateMobSpawningFlags();
        this.updateEffectiveRespawnData();
        ci.cancel();
    }
}
