package net.ashpapi.papioptimization.mixin.weather;

import net.ashpapi.papioptimization.core.FastWeatherHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelWeatherMixin {

    /**
     * Fast-exit if not thundering or not raining:
     * Vanilla pushes "thunder" to the profiler on every single chunk every tick even in clear weather.
     * Canceling here saves hundreds of calls per tick per world.
     */
    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void papi$fastThunder(LevelChunk chunk, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!self.isRaining() || !self.isThundering()) {
            ci.cancel();
        }
    }

    /**
     * Optimized tickPrecipitation:
     * - Fast-paths warm/temperate biomes where freezing is impossible.
     * - Fast-paths cauldron check before virtual invocation.
     * - Preserves 100% exact vanilla rules and probabilities.
     */
    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
    private void papi$fastPrecipitation(BlockPos pos, CallbackInfo ci) {
        FastWeatherHandler.tickPrecipitationFast((ServerLevel) (Object) this, pos);
        ci.cancel();
    }
}
