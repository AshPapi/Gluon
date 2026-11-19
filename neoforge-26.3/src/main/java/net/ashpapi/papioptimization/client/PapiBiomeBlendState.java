package net.ashpapi.papioptimization.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

public final class PapiBiomeBlendState {
    public final BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
    public final Biome[] paletteBiomes = new Biome[8];
    public final int[] paletteCounts = new int[8];
}
