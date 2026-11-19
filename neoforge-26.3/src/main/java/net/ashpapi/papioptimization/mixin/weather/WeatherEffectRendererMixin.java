package net.ashpapi.papioptimization.mixin.weather;

import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer.ColumnInstance;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WeatherEffectRenderer.class, priority = 1500)
public abstract class WeatherEffectRendererMixin {

    @Shadow
    protected abstract ColumnInstance createRainColumnInstance(RandomSource random, long ticks, int x, int bottomY, int topY, int z, int lightCoords, float partialTicks);

    @Shadow
    protected abstract ColumnInstance createSnowColumnInstance(RandomSource random, long ticks, int x, int bottomY, int topY, int z, int lightCoords, float partialTicks);

    @Unique private final RandomSource papi$random = RandomSource.create();

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void papi$fastExtractRenderState(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState, CallbackInfo ci) {
        renderState.rainColumns.clear();
        renderState.snowColumns.clear();
        renderState.intensity = level.getRainLevel(partialTicks);
        if (renderState.intensity <= 0.0f) {
            ci.cancel();
            return;
        }

        renderState.radius = Minecraft.getInstance().options.weatherRadius().get();
        int radius = renderState.radius;
        int cameraBlockX = Mth.floor(cameraPos.x);
        int cameraBlockY = Mth.floor(cameraPos.y);
        int cameraBlockZ = Mth.floor(cameraPos.z);

        boolean cullBehind = false;
        double lookX = 0.0;
        double lookZ = 0.0;
        if (!PapiOcclusionEngine.isShadowPass()) {
            Minecraft mc = Minecraft.getInstance();
            Camera cam = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
            if (cam != null && Math.abs(cam.xRot()) < 35.0F && cam.position().distanceToSqr(cameraPos) <= 1.0E-4) {
                Vector3fc fwd = cam.forwardVector();
                double hx = fwd.x();
                double hz = fwd.z();
                double hLen = Math.hypot(hx, hz);
                if (hLen > 0.25) {
                    lookX = hx / hLen;
                    lookZ = hz / hLen;
                    cullBehind = true;
                }
            }
        }

        int poolMark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos mutablePos = BlockPosPool.get();
            RandomSource random = this.papi$random;
            long gameTime = level.getGameTime();

            int lastChunkX = Integer.MIN_VALUE;
            int lastChunkZ = Integer.MIN_VALUE;
            ChunkAccess cachedChunk = null;

            for (int z = cameraBlockZ - radius; z <= cameraBlockZ + radius; ++z) {
                int currentChunkZ = z >> 4;
                double dz = ((double) z + 0.5) - cameraPos.z;
                for (int x = cameraBlockX - radius; x <= cameraBlockX + radius; ++x) {
                    if (cullBehind) {
                        double dx = ((double) x + 0.5) - cameraPos.x;
                        if (dx * dx + dz * dz > 6.25 && dx * lookX + dz * lookZ < -2.25) {
                            continue;
                        }
                    }
                    int currentChunkX = x >> 4;
                    if (cachedChunk == null || currentChunkX != lastChunkX || currentChunkZ != lastChunkZ) {
                        cachedChunk = level.getChunkSource().getChunk(currentChunkX, currentChunkZ, false);
                        lastChunkX = currentChunkX;
                        lastChunkZ = currentChunkZ;
                    }

                    if (cachedChunk == null) continue;

                    int terrainHeight = cachedChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
                    int y0 = Math.max(cameraBlockY - radius, terrainHeight);
                    int y1 = Math.max(cameraBlockY + radius, terrainHeight);
                    if (y1 - y0 == 0) continue;

                    Biome.Precipitation precipitation = level.getPrecipitationAt(mutablePos.set(x, cameraBlockY, z));
                    if (precipitation == Biome.Precipitation.NONE) continue;

                    int seed = x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761;
                    random.setSeed((long) seed);
                    int lightSampleY = Math.max(cameraBlockY, terrainHeight);
                    int lightCoords = LightCoordsUtil.getLightCoords((BlockAndLightGetter) level, mutablePos.set(x, lightSampleY, z));

                    if (precipitation == Biome.Precipitation.RAIN) {
                        renderState.rainColumns.add(this.createRainColumnInstance(random, gameTime, x, y0, y1, z, lightCoords, partialTicks));
                    } else if (precipitation == Biome.Precipitation.SNOW) {
                        renderState.snowColumns.add(this.createSnowColumnInstance(random, gameTime, x, y0, y1, z, lightCoords, partialTicks));
                    }
                }
            }
        } finally {
            BlockPosPool.reset(poolMark);
        }

        ci.cancel();
    }
}
