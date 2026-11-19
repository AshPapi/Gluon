package net.ashpapi.papioptimization.access;

import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.world.level.block.state.BlockState;

public interface PapiNoisiumSection {

    void papi$setBlockStateNoisium(int sectionX, int sectionY, int sectionZ, BlockState state, NoisiumPaletteCursor cursor);
}
