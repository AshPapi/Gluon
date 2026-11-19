package net.ashpapi.papioptimization.worldgen;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.lang.ref.WeakReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class JigsawStateCache {
    public WeakReference<HolderLookup<Block>> lookupRef = new WeakReference<>(null);
    public final Object2ObjectOpenHashMap<String, BlockState> map = new Object2ObjectOpenHashMap<>(64);
}
