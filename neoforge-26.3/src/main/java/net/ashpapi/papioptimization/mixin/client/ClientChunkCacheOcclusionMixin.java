package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientChunkCache.class, priority = 1500)
public abstract class ClientChunkCacheOcclusionMixin {

    @Shadow @Final ClientLevel level;

    @WrapMethod(method = "replaceWithPacketData")
    private LevelChunk papi$guardOcclusionOnChunkPacket(
            int x,
            int z,
            ClientboundLevelChunkPacketData packetData,
            Operation<LevelChunk> original
    ) {
        ClientLevel lvl = this.level;
        if (lvl == null) {
            return original.call(x, z, packetData);
        }
        int minSec = lvl.getMinSectionY();
        int maxSec = lvl.getMaxSectionY();
        PapiOcclusionEngine.beginChunkWrite(x, z, minSec, maxSec);
        try {
            return original.call(x, z, packetData);
        } finally {
            PapiOcclusionEngine.endChunkWrite(x, z, minSec, maxSec);
        }
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void papi$invalidateOcclusionOnChunkDrop(ChunkPos pos, CallbackInfo ci) {
        if (pos != null && this.level != null) {
            PapiOcclusionEngine.invalidateChunk(pos.x(), pos.z(), this.level.getMinSectionY(), this.level.getMaxSectionY());
        }
    }
}
