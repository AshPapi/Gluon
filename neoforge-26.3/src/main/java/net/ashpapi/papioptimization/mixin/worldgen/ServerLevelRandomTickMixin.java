package net.ashpapi.papioptimization.mixin.worldgen;

import net.ashpapi.papioptimization.mixin.core.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelRandomTickMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickChunk(LevelChunk chunk, int tickSpeed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        LevelAccessor acc = (LevelAccessor) this;
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        ProfilerFiller profiler = Profiler.get();

        profiler.push("iceandsnow");
        for (int i = 0; i < tickSpeed; ++i) {
            if (level.getRandom().nextInt(48) != 0) continue;
            level.tickPrecipitation(level.getBlockRandomPos(minX, 0, minZ, 15));
        }

        profiler.popPush("tickBlocks");
        if (tickSpeed > 0) {
            LevelChunkSection[] sections = chunk.getSections();
            int rand = acc.papi$getRandValue();

            for (int sectionIndex = 0; sectionIndex < sections.length; ++sectionIndex) {
                LevelChunkSection section = sections[sectionIndex];
                if (!section.isRandomlyTicking()) continue;

                int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                int minYInSection = SectionPos.sectionToBlockCoord(sectionY);

                for (int i = 0; i < tickSpeed; ++i) {
                    rand = rand * 3 + 1013904223;
                    int val = rand >> 2;
                    int rx = val & 15;
                    int ry = (val >> 16) & 15;
                    int rz = (val >> 8) & 15;

                    BlockState blockState = section.getBlockState(rx, ry, rz);
                    boolean blockTicks = blockState.isRandomlyTicking();
                    FluidState fluidState = blockState.getFluidState();
                    boolean fluidTicks = fluidState.isRandomlyTicking();

                    if (blockTicks || fluidTicks) {
                        acc.papi$setRandValue(rand);
                        BlockPos pos = new BlockPos(minX + rx, minYInSection + ry, minZ + rz);
                        profiler.push("randomTick");
                        if (blockTicks) {
                            blockState.randomTick(level, pos, level.getRandom());
                        }
                        if (fluidTicks) {
                            fluidState.randomTick(level, pos, level.getRandom());
                        }
                        profiler.pop();
                        rand = acc.papi$getRandValue();
                    }
                }
            }
            acc.papi$setRandValue(rand);
        }
        profiler.pop();
        ci.cancel();
    }
}
