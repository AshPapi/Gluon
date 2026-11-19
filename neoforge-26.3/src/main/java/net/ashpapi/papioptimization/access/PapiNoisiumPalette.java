package net.ashpapi.papioptimization.access;

import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.world.level.block.state.BlockState;

public interface PapiNoisiumPalette {

    BlockState papi$setDirectNoisium(int index, BlockState state, NoisiumPaletteCursor cursor);
}
