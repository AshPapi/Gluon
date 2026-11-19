package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelOcclusionInvalidationMixin {

    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean papi$guardOcclusionOnSetBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            Operation<Boolean> original
    ) {
        PapiOcclusionEngine.beginBlockWrite(pos);
        try {
            return original.call(pos, state, flags, recursionLeft);
        } finally {
            PapiOcclusionEngine.endBlockWrite(pos);
        }
    }

    @WrapMethod(method = "setServerVerifiedBlockState")
    private void papi$guardOcclusionOnServerVerifiedBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            Operation<Void> original
    ) {
        PapiOcclusionEngine.beginBlockWrite(pos);
        try {
            original.call(pos, state, flags);
        } finally {
            PapiOcclusionEngine.endBlockWrite(pos);
        }
    }

    @Inject(method = "unload", at = @At("RETURN"))
    private void papi$invalidateOcclusionOnChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        if (chunk != null) {
            ChunkPos pos = chunk.getPos();
            PapiOcclusionEngine.invalidateChunk(pos.x(), pos.z(), chunk.getMinSectionY(), chunk.getMaxSectionY());
        }
    }
}
