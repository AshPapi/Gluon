package net.ashpapi.papioptimization.mixin.weather;

import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelWeatherMixin {

    @Shadow private int rainSoundTime;

    @Inject(
        method = "tickWeatherEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;createThreadLocalInstance(J)Lnet/minecraft/util/RandomSource;"
        ),
        cancellable = true
    )
    private void papi$fastTickWeatherEffects(
            CallbackInfo ci,
            @Local Camera camera,
            @Local ParticleStatus particleStatus,
            @Local int weatherRadius,
            @Local float rainLevel
    ) {
        ci.cancel();

        ClientLevel self = (ClientLevel) (Object) this;
        RandomSource random = RandomSource.createThreadLocalInstance(self.getGameTime() * 312987231L);
        BlockPos cameraPosition = BlockPos.containing(camera.position());
        int camX = cameraPosition.getX();
        int camY = cameraPosition.getY();
        int camZ = cameraPosition.getZ();

        boolean hasRainParticle = false;
        int rainParticleX = 0;
        int rainParticleY = 0;
        int rainParticleZ = 0;

        int weatherDiameter = 2 * weatherRadius + 1;
        int weatherArea = weatherDiameter * weatherDiameter;
        int rainParticles = (int) (0.225f * (float) weatherArea * rainLevel * rainLevel)
                / (particleStatus == ParticleStatus.DECREASED ? 2 : 1);

        int minY = self.getMinY();
        int poolMark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos heightmapPos = BlockPosPool.get();
            BlockPos.MutableBlockPos particleBlockPos = BlockPosPool.get();

            for (int ii = 0; ii < rainParticles; ++ii) {
                int x = random.nextInt(weatherDiameter) - weatherRadius;
                int z = random.nextInt(weatherDiameter) - weatherRadius;
                int worldX = camX + x;
                int worldZ = camZ + z;

                int topY = self.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);

                if (topY <= minY || topY > camY + 10 || topY < camY - 10) {
                    continue;
                }

                heightmapPos.set(worldX, topY, worldZ);
                if (self.getPrecipitationAt(heightmapPos) != Biome.Precipitation.RAIN) {
                    continue;
                }

                particleBlockPos.set(worldX, topY - 1, worldZ);
                hasRainParticle = true;
                rainParticleX = worldX;
                rainParticleY = topY - 1;
                rainParticleZ = worldZ;

                if (particleStatus == ParticleStatus.MINIMAL) {
                    break;
                }

                double blockX = random.nextDouble();
                double blockZ = random.nextDouble();
                BlockState block = self.getBlockState(particleBlockPos);
                FluidState fluid = self.getFluidState(particleBlockPos);

                double particleY;
                boolean fluidEmpty = fluid.isEmpty();
                if (fluidEmpty && block.isCollisionShapeFullBlock(self, particleBlockPos)) {
                    particleY = 1.0;
                } else {
                    VoxelShape blockShape = block.getCollisionShape(self, particleBlockPos);
                    double blockTop = blockShape.max(Direction.Axis.Y, blockX, blockZ);
                    double fluidTop = fluidEmpty ? 0.0 : fluid.getHeight(self, particleBlockPos);
                    particleY = Math.max(blockTop, fluidTop);
                }

                SimpleParticleType particleType = !fluid.is(FluidTags.LAVA) && !block.is(Blocks.MAGMA_BLOCK) && !CampfireBlock.isLitCampfire(block)
                        ? ParticleTypes.RAIN
                        : ParticleTypes.SMOKE;

                self.addParticle(particleType,
                        (double) particleBlockPos.getX() + blockX,
                        (double) particleBlockPos.getY() + particleY,
                        (double) particleBlockPos.getZ() + blockZ,
                        0.0, 0.0, 0.0);
            }

            if (hasRainParticle && random.nextInt(3) < this.rainSoundTime++) {
                this.rainSoundTime = 0;
                BlockPos.MutableBlockPos rainSoundPos = heightmapPos.set(rainParticleX, rainParticleY, rainParticleZ);
                int camTopY = self.getHeight(Heightmap.Types.MOTION_BLOCKING, camX, camZ);
                if (rainParticleY > camY + 1 && camTopY > Mth.floor((float) camY)) {
                    self.playLocalSound(rainSoundPos, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1f, 0.5f, false);
                } else {
                    self.playLocalSound(rainSoundPos, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2f, 1.0f, false);
                }
            }
        } finally {
            BlockPosPool.reset(poolMark);
        }
    }
}
