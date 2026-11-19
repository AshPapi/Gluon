package net.ashpapi.papioptimization.mixin.chunk;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin {

    @Shadow @Final private Set<ChunkHolder> chunkHoldersToBroadcast;

    @Inject(method = "broadcastChangedChunks", at = @At("HEAD"), cancellable = true)
    private void papi$skipEmptyBroadcast(ProfilerFiller profiler, CallbackInfo ci) {
        if (this.chunkHoldersToBroadcast.isEmpty()) {
            ci.cancel();
        }
    }
}
