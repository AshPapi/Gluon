package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.access.PapiNoisiumSection;
import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1500)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Shadow
    @Final
    private static BlockState AIR;

    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    protected abstract BlockState debugPreliminarySurfaceLevel(NoiseChunk noiseChunk, int x, int y, int z, BlockState state);

    @Unique
    private BlockState papi$defaultBlock;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$cacheDefaultBlock(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, CallbackInfo ci) {
        if (settings != null && settings.isBound()) {
            this.papi$defaultBlock = settings.value().defaultBlock();
        }
    }

    @WrapOperation(
        method = "createNoiseChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Beardifier;forStructuresInChunk(Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/levelgen/Beardifier;"
        )
    )
    private Beardifier papi$skipEmptyBeardifier(
            StructureManager structureManager,
            ChunkPos chunkPos,
            Operation<Beardifier> original,
            @Local(argsOnly = true) ChunkAccess chunkAccess
    ) {
        if (chunkAccess != null && chunkAccess.getAllReferences().isEmpty()) {
            return Beardifier.EMPTY;
        }
        return original.call(structureManager, chunkPos);
    }

    @WrapMethod(method = "doFill")
    private void papi$fastDoFill(NoiseChunk noiseChunk, ChunkAccess chunkAccess, Operation<Void> original) {
        Heightmap oceanFloor = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Aquifer aquifer = noiseChunk.aquifer();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        DensityVolume volume = noiseChunk.volume();
        NoiseGeneratorSettings genSettings = this.settings.value();
        DensitySampler.Bound finalDensitySampler = noiseChunk.cachingSamplers().get(genSettings.noiseRouter().finalDensity());
        NoisiumPaletteCursor paletteCursor = new NoisiumPaletteCursor();
        try (ScopedDensityBuffer densityBuffer = finalDensitySampler.sampleVolume(volume)) {
            BlockState defaultBlock = this.papi$defaultBlock != null ? this.papi$defaultBlock : genSettings.defaultBlock();
            boolean debugAquifers = SharedConstants.DEBUG_AQUIFERS;
            int sizeZ = volume.sizeZ();
            int sizeX = volume.sizeX();
            int sizeY = volume.sizeY();
            int lastSecIdx = Integer.MIN_VALUE;
            LevelChunkSection section = null;
            PapiNoisiumSection noisiumSection = null;
            for (int zIdx = 0; zIdx < sizeZ; zIdx++) {
                int blockZ = volume.blockZ(zIdx);
                for (int xIdx = 0; xIdx < sizeX; xIdx++) {
                    int blockX = volume.blockX(xIdx);
                    for (int yIdx = sizeY - 1; yIdx >= 0; yIdx--) {
                        int blockY = volume.blockY(yIdx);
                        float density = densityBuffer.get(volume.indexUnchecked(xIdx, yIdx, zIdx));
                        BlockState state = aquifer.computeSubstance(blockX, blockY, blockZ, (double) density);
                        if (state == null) {
                            state = defaultBlock;
                        }
                        if (debugAquifers) {
                            state = this.debugPreliminarySurfaceLevel(noiseChunk, blockX, blockY, blockZ, state);
                        }
                        if (state == AIR) {
                            continue;
                        }
                        int secIdx = chunkAccess.getSectionIndex(blockY);
                        if (secIdx != lastSecIdx) {
                            lastSecIdx = secIdx;
                            section = chunkAccess.getSection(secIdx);
                            noisiumSection = (section instanceof PapiNoisiumSection ns) ? ns : null;
                            paletteCursor.reset();
                        }
                        int relY = SectionPos.sectionRelative(blockY);
                        if (noisiumSection != null) {
                            noisiumSection.papi$setBlockStateNoisium(xIdx, relY, zIdx, state, paletteCursor);
                        } else {
                            section.setBlockState(xIdx, relY, zIdx, state, false);
                        }
                        oceanFloor.update(xIdx, blockY, zIdx, state);
                        worldSurface.update(xIdx, blockY, zIdx, state);
                        if (aquifer.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
                            mutablePos.set(blockX, blockY, blockZ);
                            chunkAccess.markPosForPostProcessing(mutablePos);
                        }
                    }
                }
            }
        }
    }
}
